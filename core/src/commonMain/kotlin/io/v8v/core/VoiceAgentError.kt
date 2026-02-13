package io.v8v.core

/**
 * Structured error types emitted by [VoiceAgent].
 *
 * Consumers can pattern-match on the sealed subtypes to handle
 * different failure modes (permission denied, engine error, action failure)
 * with distinct UI or recovery logic.
 */
sealed class VoiceAgentError {
    /** Human-readable description of the error. */
    abstract val message: String

    /** Microphone or speech permission was not granted. */
    data class PermissionDenied(
        val status: PermissionStatus,
        override val message: String = "Microphone permission not granted. Current status: $status",
    ) : VoiceAgentError()

    /** The speech recognition engine reported an error. */
    data class EngineError(
        val code: Int,
        override val message: String,
    ) : VoiceAgentError()

    /** A dispatched action handler returned a failure. */
    data class ActionFailed(
        val intent: String,
        val scope: ActionScope,
        override val message: String,
    ) : VoiceAgentError()
}
