package io.v8v.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
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
 * iOS implementation of [SpeechRecognitionEngine] using the Speech framework.
 *
 * Uses [SFSpeechRecognizer] for speech-to-text, [AVAudioEngine] for audio input,
 * and [AVAudioSession] for managing the recording session.
 *
 * IMPORTANT: The audio engine must be started BEFORE the recognition task,
 * otherwise the recognition task times out waiting for audio and cancels.
 *
 * **Requirements:**
 * - `NSSpeechRecognitionUsageDescription` in Info.plist
 * - `NSMicrophoneUsageDescription` in Info.plist
 */
@OptIn(ExperimentalForeignApi::class)
class IosSpeechEngine : SpeechRecognitionEngine {

    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SpeechEvent> = _events.asSharedFlow()

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var audioEngine: AVAudioEngine? = null
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest? = null
    private var recognitionTask: SFSpeechRecognitionTask? = null
    private var speechRecognizer: SFSpeechRecognizer? = null

    /** Guard against re-entrant stopListening calls. */
    private var isStopping = false

    override fun startListening(language: String) {
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

        // Configure audio session for recording on iOS
        try {
            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setActive(true, error = null)
        } catch (_: Exception) {
            // Continue even if session activation fails
        }

        // Create recognition request
        val request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        if (speechRecognizer?.supportsOnDeviceRecognition() == true) {
            request.requiresOnDeviceRecognition = true
        }
        recognitionRequest = request

        // ── Step 1: Set up audio engine and install tap FIRST ──
        val engine = AVAudioEngine()
        audioEngine = engine

        val inputNode = engine.inputNode
        val recordingFormat = inputNode.outputFormatForBus(0u)

        inputNode.installTapOnBus(0u, bufferSize = 4096u, format = recordingFormat) { buffer, _ ->
            if (buffer != null) {
                recognitionRequest?.appendAudioPCMBuffer(buffer)

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
                        val normalized = ((rmsdB + 50f).coerceIn(0f, 50f)) / 50f
                        _events.tryEmit(SpeechEvent.RmsChanged(normalized))
                    }
                }
            }
        }

        // ── Step 2: Start the audio engine so audio is flowing ──
        engine.prepare()
        try {
            engine.startAndReturnError(null)
        } catch (e: Exception) {
            _events.tryEmit(SpeechEvent.Error(code = -3, message = "Audio engine start failed: ${e.message}"))
            return
        }

        // ── Step 3: Start recognition task AFTER audio is already flowing ──
        recognitionTask = speechRecognizer!!.recognitionTaskWithRequest(request) { result, error ->
            if (error != null) {
                // Only emit the error — do NOT call stopListening() here.
                // The VoiceAgent decides whether to restart or stop.
                _events.tryEmit(
                    SpeechEvent.Error(
                        code = error.code.toInt(),
                        message = error.localizedDescription ?: "Recognition error",
                    ),
                )
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

        _isListening.value = true
        _events.tryEmit(SpeechEvent.ReadyForSpeech)
    }

    override fun stopListening() {
        // Guard against re-entrant calls
        if (isStopping) return
        isStopping = true

        audioEngine?.stop()
        audioEngine?.inputNode?.removeTapOnBus(0u)
        audioEngine = null

        recognitionRequest?.endAudio()
        recognitionRequest = null

        recognitionTask?.cancel()
        recognitionTask = null

        _isListening.value = false

        isStopping = false
    }

    override fun destroy() {
        stopListening()
        speechRecognizer = null
    }
}
