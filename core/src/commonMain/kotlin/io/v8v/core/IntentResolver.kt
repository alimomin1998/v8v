package io.v8v.core

import io.v8v.core.model.ResolvedIntent

/**
 * Rule-based intent resolver that matches spoken text against registered
 * wildcard phrase patterns.
 *
 * Patterns support two kinds of capture tokens:
 * - `*` — anonymous wildcard that captures one or more words. Captured text
 *   is joined into [ResolvedIntent.extractedText].
 * - `{name}` — named slot that captures one or more words into
 *   [ResolvedIntent.slots] under the given key.
 *
 * Examples:
 * - `"add * to todo"` matches `"add buy milk to todo"` → extractedText = `"buy milk"`
 * - `"remind me to {task} at {time}"` matches `"remind me to buy milk at 5pm"`
 *   → slots = `{"task": "buy milk", "time": "5pm"}`
 *
 * Both can be mixed in one pattern: `"add {item} to * list"`.
 *
 * Matching is case-insensitive. Language-specific patterns are tried first;
 * if no match is found for the requested language, all patterns are tried
 * as a fallback.
 */
class IntentResolver {

    /**
     * Descriptor for a single capture group in a pattern.
     * [name] is `null` for anonymous `*` wildcards.
     */
    private data class SlotDescriptor(val name: String?)

    private data class RegisteredPattern(
        val intent: String,
        val language: String,
        val phrase: String,
        val regex: Regex,
        /** Ordered list of capture group descriptors (one per `*` or `{name}`). */
        val slotDescriptors: List<SlotDescriptor>,
    )

    private val patterns = mutableListOf<RegisteredPattern>()

    /**
     * Register phrase patterns for an intent.
     *
     * @param intent Intent identifier (e.g. "todo.add").
     * @param phrases Map of language code to list of patterns. Each pattern
     *   may contain `*` wildcards and/or `{name}` named slots.
     */
    fun register(intent: String, phrases: Map<String, List<String>>) {
        for ((language, phraseList) in phrases) {
            for (phrase in phraseList) {
                val normalized = phrase.trim().lowercase()
                val (regex, descriptors) = buildRegex(normalized)
                patterns.add(RegisteredPattern(intent, language, normalized, regex, descriptors))
            }
        }
    }

    /**
     * Try to match [text] against registered patterns.
     *
     * Matching proceeds in up to three passes:
     * 1. Exact regex match for the requested [language].
     * 2. Exact regex match for all other languages (fallback).
     * 3. Fuzzy token-overlap match when [fuzzyThreshold] > 0 and
     *    exact matching failed. The best-scoring pattern above the
     *    threshold wins; its confidence is set to the overlap score.
     *
     * @param text The transcribed speech text.
     * @param language The language code to prefer when matching.
     * @param fuzzyThreshold Minimum word-overlap score (0.0–1.0) for the
     *   fuzzy pass. `0.0` disables fuzzy matching (default).
     * @return A [ResolvedIntent] if a match is found, or `null`.
     */
    fun resolve(
        text: String,
        language: String,
        fuzzyThreshold: Float = 0.0f,
    ): ResolvedIntent? {
        val normalized = text.trim().lowercase()

        // First pass: try patterns registered for the requested language.
        for (pattern in patterns) {
            if (pattern.language != language) continue
            val match = pattern.regex.matchEntire(normalized)
            if (match != null) {
                return buildResult(match, pattern, text, language)
            }
        }

        // Second pass: try all patterns regardless of language.
        for (pattern in patterns) {
            if (pattern.language == language) continue // already tried
            val match = pattern.regex.matchEntire(normalized)
            if (match != null) {
                return buildResult(match, pattern, text, pattern.language)
            }
        }

        // Third pass: fuzzy token-overlap matching.
        if (fuzzyThreshold > 0f) {
            return fuzzyResolve(normalized, text, language, fuzzyThreshold)
        }

        return null
    }

    /** Remove all registered patterns. */
    fun clear() {
        patterns.clear()
    }

    // ---- internal helpers ----

    /**
     * Fuzzy fallback: score every pattern by word-overlap and return the
     * best match above [threshold].
     *
     * Only the non-wildcard (literal) tokens of each pattern are considered.
     * The score is `matchingTokens / totalPatternTokens`.
     */
    private fun fuzzyResolve(
        normalizedInput: String,
        rawText: String,
        language: String,
        threshold: Float,
    ): ResolvedIntent? {
        val inputWords = normalizedInput.split("\\s+".toRegex()).toSet()

        var bestPattern: RegisteredPattern? = null
        var bestScore = 0f

        // Prefer the requested language by trying it first.
        val sorted = patterns.sortedByDescending { it.language == language }

        for (pattern in sorted) {
            val score = tokenOverlapScore(pattern.phrase, inputWords)
            if (score > bestScore) {
                bestScore = score
                bestPattern = pattern
            }
        }

        if (bestPattern == null || bestScore < threshold) return null

        // For fuzzy matches, extractedText is the full input (since we
        // cannot reliably identify wildcard regions without a regex match).
        return ResolvedIntent(
            intent = bestPattern.intent,
            extractedText = rawText,
            rawText = rawText,
            language = bestPattern.language,
            confidence = bestScore,
        )
    }

    /**
     * Compute the Dice similarity coefficient between the literal (non-wildcard)
     * tokens in [phrase] and the words in [inputWords].
     *
     * Returns a value in 0.0 (no overlap) to 1.0 (identical word sets).
     *
     * ## Formula
     *
     * ```
     * Dice = (2 × |intersection|) / (|A| + |B|)
     * ```
     *
     * Where **A** = input word tokens, **B** = pattern literal word tokens
     * (wildcards `*` and named slots `{name}` are stripped).
     *
     * ## Why Dice instead of simple overlap?
     *
     * Simple overlap (`|intersection| / |B|`) would give 1.0 if all pattern
     * words appear in the input, regardless of how many extra filler words
     * the user added. For example:
     *
     * ```
     * Input:   "please could you add milk to my todo list"  (9 words)
     * Pattern: "add * to todo"  →  {add, to, todo}          (3 literal words)
     * Simple overlap = 3/3 = 1.0   ← misleadingly perfect
     * Dice           = (2×3)/(9+3) = 0.50  ← correctly penalizes filler
     * ```
     *
     * Meanwhile, a close match scores high:
     * ```
     * Input:   "add milk to todo"  (4 words)
     * Dice    = (2×3)/(4+3) = 0.86  ← correctly high
     * ```
     *
     * This makes the `fuzzyThreshold` setting meaningful: at 0.5 you accept
     * loose matches with filler, at 0.8 you require close phrasing.
     */
    private fun tokenOverlapScore(phrase: String, inputWords: Set<String>): Float {
        // Remove wildcards and slot placeholders, then tokenize.
        val cleaned = phrase
            .replace("*", " ")
            .replace(Regex("""\{\w+\}"""), " ")
        val patternTokens = cleaned.split("\\s+".toRegex()).filter { it.isNotEmpty() }.toSet()
        if (patternTokens.isEmpty()) return 0f

        val intersection = patternTokens.count { it in inputWords }
        return (2f * intersection) / (patternTokens.size + inputWords.size).toFloat()
    }

    private fun buildResult(
        match: MatchResult,
        pattern: RegisteredPattern,
        rawText: String,
        language: String,
    ): ResolvedIntent {
        val groups = match.groupValues.drop(1) // first element is the full match
        val anonymousParts = mutableListOf<String>()
        val slots = mutableMapOf<String, String>()

        for ((index, descriptor) in pattern.slotDescriptors.withIndex()) {
            val value = groups.getOrElse(index) { "" }.trim()
            if (descriptor.name != null) {
                slots[descriptor.name] = value
            } else {
                anonymousParts.add(value)
            }
        }

        return ResolvedIntent(
            intent = pattern.intent,
            extractedText = anonymousParts.joinToString(" ").trim(),
            rawText = rawText,
            language = language,
            slots = slots,
        )
    }

    /**
     * Convert a phrase pattern into a [Regex] and a list of [SlotDescriptor]s.
     *
     * Non-wildcard/slot segments are escaped. Each `*` and `{name}` becomes
     * a `(.+)` capture group. The result is anchored with `^...$`.
     */
    private fun buildRegex(phrase: String): Pair<Regex, List<SlotDescriptor>> {
        val descriptors = mutableListOf<SlotDescriptor>()
        val regexBuilder = StringBuilder("^")

        // Tokenize by splitting on `*` and `{name}` tokens.
        val tokenPattern = Regex("""\*|\{(\w+)\}""")
        var lastEnd = 0

        for (tokenMatch in tokenPattern.findAll(phrase)) {
            // Append escaped literal text before this token.
            val literal = phrase.substring(lastEnd, tokenMatch.range.first)
            regexBuilder.append(Regex.escape(literal))

            // Append capture group.
            regexBuilder.append("(.+)")

            // Record whether this is anonymous or named.
            val slotName = tokenMatch.groupValues[1] // empty string for `*`
            descriptors.add(SlotDescriptor(name = slotName.ifEmpty { null }))

            lastEnd = tokenMatch.range.last + 1
        }

        // Append any trailing literal text.
        if (lastEnd < phrase.length) {
            regexBuilder.append(Regex.escape(phrase.substring(lastEnd)))
        }

        regexBuilder.append("$")
        return Regex(regexBuilder.toString()) to descriptors
    }
}
