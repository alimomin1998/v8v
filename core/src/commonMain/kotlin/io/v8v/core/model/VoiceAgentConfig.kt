package io.v8v.core.model

/**
 * Configuration for a [io.v8v.core.VoiceAgent] instance.
 *
 * @property language BCP-47 language tag for speech recognition (e.g. "en", "en-IN", "hi").
 * @property continuous If true, the agent auto-restarts listening after each utterance.
 * @property partialResults If true, partial transcription events are forwarded to the transcript flow.
 * @property fuzzyThreshold Minimum word-overlap score (0.0–1.0) for fuzzy intent matching.
 *   When `0.0` (default), only exact regex matches are used. When > 0, a fuzzy
 *   fallback pass is attempted after exact matching fails. A typical value is `0.6`.
 * @property silenceTimeoutMs Time (in ms) after the last partial result to automatically
 *   promote it to a final result and trigger intent resolution. This handles speech
 *   engines (like Apple's SFSpeechRecognizer) that don't reliably set `isFinal`.
 *   Set to `0` to disable (only rely on engine's own `isFinal` flag).
 */
data class VoiceAgentConfig(
    val language: String = "en",
    val continuous: Boolean = true,
    val partialResults: Boolean = false,
    val fuzzyThreshold: Float = 0.0f,
    val silenceTimeoutMs: Long = 1500L,
)
