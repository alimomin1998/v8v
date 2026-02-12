package io.v8v.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.setActive
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Foundation.NSLocale
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Apple platform implementation of [SpeechRecognitionEngine] using the Speech framework.
 *
 * Works on both **iOS** and **macOS** — uses [SFSpeechRecognizer] for speech-to-text
 * and [AVAudioEngine] for audio input. Both APIs are available on iOS 10+ and macOS 10.15+.
 *
 * Maps recognition results to the framework's [SpeechEvent] sealed class:
 * - Partial transcripts -> [SpeechEvent.PartialResult]
 * - Final transcripts -> [SpeechEvent.FinalResult]
 * - Audio buffer RMS -> [SpeechEvent.RmsChanged] (pre-normalized to 0.0-1.0)
 * - Errors -> [SpeechEvent.Error]
 *
 * **Requirements (iOS):**
 * - `NSSpeechRecognitionUsageDescription` in Info.plist
 * - `NSMicrophoneUsageDescription` in Info.plist
 *
 * **Requirements (macOS):**
 * - Microphone entitlement (`com.apple.security.device.audio-input`)
 * - `NSSpeechRecognitionUsageDescription` in Info.plist
 */
class AppleSpeechEngine : SpeechRecognitionEngine {

    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SpeechEvent> = _events.asSharedFlow()

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var audioEngine: AVAudioEngine? = null
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest? = null
    private var recognitionTask: SFSpeechRecognitionTask? = null
    private var speechRecognizer: SFSpeechRecognizer? = null

    override fun startListening(language: String) {
        // Cancel any existing task
        stopListening()

        val locale = NSLocale(localeIdentifier = language)
        speechRecognizer = SFSpeechRecognizer(locale = locale)

        if (speechRecognizer?.isAvailable() != true) {
            _events.tryEmit(
                SpeechEvent.Error(
                    code = -1,
                    message = "SFSpeechRecognizer not available for locale: $language",
                ),
            )
            return
        }

        // Configure audio session.
        // On iOS, setCategory is needed to select the recording category.
        // On macOS, AVAudioSession exists but category management works differently.
        // We wrap in try/catch to handle both platforms gracefully.
        try {
            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setActive(true, error = null)
        } catch (_: Exception) {
            // macOS may not require explicit audio session activation — continue.
        }

        val request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        recognitionRequest = request

        val engine = AVAudioEngine()
        audioEngine = engine

        recognitionTask = speechRecognizer!!.recognitionTaskWithRequest(request) { result, error ->
            if (error != null) {
                _events.tryEmit(
                    SpeechEvent.Error(
                        code = error.code.toInt(),
                        message = error.localizedDescription ?: "Recognition error",
                    ),
                )
                stopListening()
                return@recognitionTaskWithRequest
            }

            if (result != null) {
                val transcript = result.bestTranscription.formattedString
                if (result.isFinal()) {
                    _events.tryEmit(SpeechEvent.FinalResult(transcript))
                } else {
                    _events.tryEmit(SpeechEvent.PartialResult(transcript))
                }
            }
        }

        // Install audio tap to feed audio buffers and extract RMS
        val inputNode = engine.inputNode
        val recordingFormat = inputNode.outputFormatForBus(0u)

        inputNode.installTapOnBus(0u, bufferSize = 1024u, format = recordingFormat) { buffer, _ ->
            if (buffer != null) {
                recognitionRequest?.appendAudioPCMBuffer(buffer)

                // Calculate RMS from the first channel
                val channelData = buffer.floatChannelData
                if (channelData != null) {
                    val frameLength = buffer.frameLength.toInt()
                    val samples = channelData[0]
                    if (samples != null && frameLength > 0) {
                        var sumSquares = 0.0f
                        for (i in 0 until frameLength) {
                            val sample = samples[i]
                            sumSquares += sample * sample
                        }
                        val rms = sqrt(sumSquares / frameLength)
                        val rmsdB = if (rms > 0f) 20f * log10(rms) else -160f
                        // Apple's AVAudioEngine RMS typically ranges from -50 to 0 dB.
                        // Normalize to 0.0-1.0 before emitting.
                        val normalized = ((rmsdB + 50f).coerceIn(0f, 50f)) / 50f
                        _events.tryEmit(SpeechEvent.RmsChanged(normalized))
                    }
                }
            }
        }

        engine.prepare()
        try {
            engine.startAndReturnError(null)
            _isListening.value = true
            _events.tryEmit(SpeechEvent.ReadyForSpeech)
        } catch (e: Exception) {
            _events.tryEmit(SpeechEvent.Error(code = -3, message = "Audio engine start failed: ${e.message}"))
        }
    }

    override fun stopListening() {
        audioEngine?.stop()
        audioEngine?.inputNode?.removeTapOnBus(0u)
        audioEngine = null

        recognitionRequest?.endAudio()
        recognitionRequest = null

        recognitionTask?.cancel()
        recognitionTask = null

        _isListening.value = false
        _events.tryEmit(SpeechEvent.EndOfSpeech)
    }

    override fun destroy() {
        stopListening()
        speechRecognizer = null
    }
}
