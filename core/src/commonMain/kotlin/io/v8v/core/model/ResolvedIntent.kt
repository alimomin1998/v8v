package io.v8v.core.model

/**
 * The result of successfully matching spoken text against a registered intent.
 *
 * @property intent The intent identifier (e.g. "todo.add").
 * @property extractedText Text captured by anonymous `*` wildcard slots, joined by spaces.
 * @property rawText The original unmodified transcript from the STT engine.
 * @property language The language code that was used for matching.
 * @property confidence Match confidence score (0.0–1.0). Rule-based matching defaults to 1.0.
 * @property slots Named slot values captured by `{name}` patterns in the phrase.
 *   For example, the pattern `"remind me to {task} at {time}"` matching
 *   `"remind me to buy milk at 5pm"` yields `slots = mapOf("task" to "buy milk", "time" to "5pm")`.
 */
data class ResolvedIntent(
    val intent: String,
    val extractedText: String,
    val rawText: String,
    val language: String,
    val confidence: Float = 1.0f,
    val slots: Map<String, String> = emptyMap(),
)
