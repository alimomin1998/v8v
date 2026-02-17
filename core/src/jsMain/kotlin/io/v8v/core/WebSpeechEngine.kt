package io.v8v.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kotlin/JS external declarations for the Web Speech API.
 *
 * Browsers expose `SpeechRecognition` (standard) or `webkitSpeechRecognition`
 * (Chrome/Edge). We use a dynamic factory to pick whichever is available.
 */
private fun createSpeechRecognition(): dynamic {
    val global = js("(typeof window !== 'undefined' ? window : self)")
    val ctor = global.SpeechRecognition ?: global.webkitSpeechRecognition
    if (ctor == null) {
        throw UnsupportedOperationException(
            "This browser does not support the Web Speech API (SpeechRecognition).",
        )
    }
    return js("new ctor()")
}

/**
 * Web Speech API implementation of [SpeechRecognitionEngine].
 *
 * Maps browser `SpeechRecognition` events to the framework's [SpeechEvent] sealed class.
 *
 * Supports:
 * - Continuous mode via `recognition.continuous`
 * - Interim (partial) results via `recognition.interimResults`
 * - Language selection via BCP-47 tags
 *
 * **Note:** The Web Speech API does not provide audio RMS data, so
 * [SpeechEvent.RmsChanged] is never emitted on this platform.
 */
class WebSpeechEngine : SpeechRecognitionEngine {
    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SpeechEvent> = _events.asSharedFlow()

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var recognition: dynamic = null
    private var currentLanguage: String = "en"

    private fun ensureRecognition() {
        if (recognition != null) return

        recognition = createSpeechRecognition()
        val rec = recognition!!

        rec.continuous = true
        rec.interimResults = true
        rec.maxAlternatives = 1

        rec.onstart = {
            _isListening.value = true
            _events.tryEmit(SpeechEvent.ReadyForSpeech)
        }

        rec.onresult = { event: dynamic ->
            val results = event.results
            val resultIndex = event.resultIndex as Int
            for (i in resultIndex until (results.length as Int)) {
                val result = results[i]
                val transcript = (result[0].transcript as String).trim()
                if (result.isFinal as Boolean) {
                    _events.tryEmit(SpeechEvent.FinalResult(transcript))
                } else {
                    _events.tryEmit(SpeechEvent.PartialResult(transcript))
                }
            }
        }

        rec.onspeechend = {
            _events.tryEmit(SpeechEvent.EndOfSpeech)
        }

        rec.onerror = { event: dynamic ->
            val errorType = event.error as String
            if (errorType != "no-speech" && errorType != "aborted") {
                _events.tryEmit(SpeechEvent.Error(code = 0, message = "Web Speech error: $errorType"))
            }
        }

        rec.onend = {
            _isListening.value = false
        }
    }

    override fun startListening(language: String) {
        try {
            ensureRecognition()
        } catch (e: Exception) {
            _events.tryEmit(SpeechEvent.Error(code = -1, message = e.message ?: "SpeechRecognition not available"))
            return
        }

        val rec = recognition!!
        currentLanguage = language
        rec.lang = language

        try {
            rec.start()
        } catch (e: dynamic) {
            _events.tryEmit(SpeechEvent.Error(code = -1, message = "Failed to start: $e"))
        }
    }

    override fun stopListening() {
        try {
            recognition?.stop()
        } catch (_: dynamic) {
            // Ignore errors on stop
        }
        _isListening.value = false
    }

    override fun destroy() {
        stopListening()
        try {
            recognition?.abort()
        } catch (_: dynamic) {
            // Ignore
        }
        recognition = null
    }
}
