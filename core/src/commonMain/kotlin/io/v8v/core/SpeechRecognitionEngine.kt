package io.v8v.core

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic interface for speech-to-text engines.
 *
 * Each platform provides its own implementation:
 * - Android → [android.speech.SpeechRecognizer]
 * - iOS → SFSpeechRecognizer (future)
 * - Web → Web Speech API (future)
 *
 * Implementations must emit [SpeechEvent]s on the [events] flow and
 * keep [isListening] up-to-date.
 */
interface SpeechRecognitionEngine {

    /** Stream of speech recognition events. Backed by a SharedFlow for multi-collector support. */
    val events: SharedFlow<SpeechEvent>

    /** Whether the engine is currently listening for speech. */
    val isListening: StateFlow<Boolean>

    /**
     * Start listening for speech in the given language.
     *
     * @param language BCP-47 language tag (e.g. "en", "en-IN", "hi").
     */
    fun startListening(language: String)

    /** Stop the current recognition session. */
    fun stopListening()

    /** Release all resources. The engine should not be used after this call. */
    fun destroy()
}
