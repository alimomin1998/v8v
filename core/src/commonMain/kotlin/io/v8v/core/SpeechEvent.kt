package io.v8v.core

/**
 * Events emitted by a [SpeechRecognitionEngine] during speech recognition.
 *
 * Consumers collect these events via [SpeechRecognitionEngine.events] to
 * react to partial transcripts, final results, errors, and lifecycle changes.
 */
sealed class SpeechEvent {
    /** Partial (in-progress) transcription while the user is still speaking. */
    data class PartialResult(
        val text: String
    ) : SpeechEvent()

    /** Final transcription after the user finishes an utterance. */
    data class FinalResult(
        val text: String
    ) : SpeechEvent()

    /** An error occurred during recognition. */
    data class Error(
        val code: Int,
        val message: String
    ) : SpeechEvent()

    /**
     * Audio input level changed. Emitted frequently while listening.
     *
     * Each platform engine normalizes its raw audio data to a 0.0–1.0 range
     * before emitting this event, so consumers can use [level] directly
     * without platform-specific knowledge.
     *
     * @property level Normalized audio level in the range 0.0 (silence) to 1.0 (loud).
     */
    data class RmsChanged(
        val level: Float
    ) : SpeechEvent()

    /** The engine is ready and actively listening for speech. */
    data object ReadyForSpeech : SpeechEvent()

    /** The user stopped speaking (end of utterance detected). */
    data object EndOfSpeech : SpeechEvent()
}
