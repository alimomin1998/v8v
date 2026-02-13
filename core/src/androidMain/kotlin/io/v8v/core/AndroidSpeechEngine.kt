package io.v8v.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation of [SpeechRecognitionEngine] using
 * [android.speech.SpeechRecognizer].
 *
 * - Runs entirely on-device (no audio is uploaded).
 * - Emits [SpeechEvent]s for partial results, final results, errors, etc.
 * - Must be created on the **main thread** (Android requirement).
 *
 * @param context Application or Activity context.
 */
class AndroidSpeechEngine(
    private val context: Context,
) : SpeechRecognitionEngine {
    private val recognizer: SpeechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(context)

    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SpeechEvent> = _events.asSharedFlow()

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var currentLanguage: String = "en"

    init {
        recognizer.setRecognitionListener(createListener())
    }

    override fun startListening(language: String) {
        currentLanguage = language
        Log.d(TAG, "startListening(language=$language)")
        val intent = createRecognizerIntent(language)
        recognizer.startListening(intent)
    }

    override fun stopListening() {
        Log.d(TAG, "stopListening()")
        recognizer.stopListening()
        _isListening.value = false
    }

    override fun destroy() {
        Log.d(TAG, "destroy()")
        recognizer.destroy()
        _isListening.value = false
    }

    // ---- private helpers ----

    private fun createRecognizerIntent(language: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                SILENCE_TIMEOUT_MS,
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                SILENCE_TIMEOUT_MS,
            )
        }

    private fun createListener(): RecognitionListener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "onReadyForSpeech")
                _isListening.value = true
                _events.tryEmit(SpeechEvent.ReadyForSpeech)
            }

            override fun onBeginningOfSpeech() {
                // No action needed; ReadyForSpeech already signals "listening".
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Android's RMS typically ranges from −2 to +10 dB.
                // Normalize to 0.0–1.0 before emitting.
                val normalized = ((rmsdB + 2f).coerceIn(0f, 12f)) / 12f
                _events.tryEmit(SpeechEvent.RmsChanged(normalized))
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Raw audio buffer — unused.
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech")
                _isListening.value = false
                _events.tryEmit(SpeechEvent.EndOfSpeech)
            }

            override fun onError(error: Int) {
                Log.e(TAG, "onError(code=$error, message=${mapErrorCode(error)})")
                _isListening.value = false
                _events.tryEmit(SpeechEvent.Error(error, mapErrorCode(error)))
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                val matches =
                    results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: return
                Log.d(TAG, "onResults(text=$text)")
                _events.tryEmit(SpeechEvent.FinalResult(text))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches =
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: return
                Log.d(TAG, "onPartialResults(text=$text)")
                _events.tryEmit(SpeechEvent.PartialResult(text))
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {
                // Reserved for future use by the platform.
            }
        }

    private companion object {
        const val TAG = "AndroidSpeechEngine"

        /** Silence timeout before the recognizer stops, in milliseconds. */
        const val SILENCE_TIMEOUT_MS = 1500L

        fun mapErrorCode(error: Int): String =
            when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timeout"
                else -> "Unknown error ($error)"
            }
    }
}
