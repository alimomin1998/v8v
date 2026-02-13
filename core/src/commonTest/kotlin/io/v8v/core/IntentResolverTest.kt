package io.v8v.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntentResolverTest {
    @Test
    fun resolves_simple_wildcard_pattern() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        val result = resolver.resolve("add buy milk to todo", "en")

        assertNotNull(result)
        assertEquals("todo.add", result.intent)
        assertEquals("buy milk", result.extractedText)
    }

    @Test
    fun returns_null_for_unmatched_text() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        val result = resolver.resolve("hello world", "en")

        assertNull(result)
    }

    @Test
    fun handles_exact_match_without_wildcards() {
        val resolver = IntentResolver()
        resolver.register("app.stop", mapOf("en" to listOf("stop listening")))

        val result = resolver.resolve("stop listening", "en")

        assertNotNull(result)
        assertEquals("app.stop", result.intent)
        assertEquals("", result.extractedText)
    }

    @Test
    fun matches_case_insensitively() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        val result = resolver.resolve("Add Buy Milk To Todo", "en")

        assertNotNull(result)
        assertEquals("buy milk", result.extractedText)
    }

    @Test
    fun supports_multilingual_phrases() {
        val resolver = IntentResolver()
        resolver.register(
            "todo.add",
            mapOf(
                "en" to listOf("add * to todo"),
                "hi" to listOf("* todo mein add karo"),
            ),
        )

        val enResult = resolver.resolve("add eggs to todo", "en")
        assertNotNull(enResult)
        assertEquals("eggs", enResult.extractedText)

        val hiResult = resolver.resolve("doodh todo mein add karo", "hi")
        assertNotNull(hiResult)
        assertEquals("doodh", hiResult.extractedText)
    }

    @Test
    fun prefers_language_specific_match() {
        val resolver = IntentResolver()
        resolver.register(
            "greet.hello",
            mapOf(
                "en" to listOf("hello"),
                "es" to listOf("hola"),
            ),
        )

        val result = resolver.resolve("hello", "en")

        assertNotNull(result)
        assertEquals("en", result.language)
    }

    @Test
    fun handles_multiple_wildcards() {
        val resolver = IntentResolver()
        resolver.register(
            "reminder.set",
            mapOf("en" to listOf("remind me to * at *")),
        )

        val result = resolver.resolve("remind me to buy milk at 5pm", "en")

        assertNotNull(result)
        assertEquals("buy milk 5pm", result.extractedText)
    }

    @Test
    fun matches_first_registered_intent_on_ambiguity() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))
        resolver.register("todo.add2", mapOf("en" to listOf("add * to todo")))

        val result = resolver.resolve("add milk to todo", "en")

        assertNotNull(result)
        assertEquals("todo.add", result.intent)
    }

    @Test
    fun clear_removes_all_patterns() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))
        resolver.clear()

        val result = resolver.resolve("add milk to todo", "en")

        assertNull(result)
    }

    @Test
    fun preserves_raw_text_in_result() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        val result = resolver.resolve("Add Buy Milk To Todo", "en")

        assertNotNull(result)
        assertEquals("Add Buy Milk To Todo", result.rawText)
    }

    @Test
    fun falls_back_to_other_languages_if_no_exact_match() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        // Requesting "fr" but pattern only exists for "en"
        val result = resolver.resolve("add eggs to todo", "fr")

        assertNotNull(result)
        assertEquals("todo.add", result.intent)
        assertEquals("en", result.language)
    }

    @Test
    fun handles_single_word_wildcard_capture() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        val result = resolver.resolve("add milk to todo", "en")

        assertNotNull(result)
        assertEquals("milk", result.extractedText)
    }

    @Test
    fun multiple_phrases_for_same_intent() {
        val resolver = IntentResolver()
        resolver.register(
            "todo.add",
            mapOf("en" to listOf("add * to todo", "add * to my list")),
        )

        val result1 = resolver.resolve("add milk to todo", "en")
        assertNotNull(result1)
        assertEquals("milk", result1.extractedText)

        val result2 = resolver.resolve("add eggs to my list", "en")
        assertNotNull(result2)
        assertEquals("eggs", result2.extractedText)
    }

    // ---- Named slot tests ----

    @Test
    fun named_slot_single() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add {item} to todo")))

        val result = resolver.resolve("add buy milk to todo", "en")

        assertNotNull(result)
        assertEquals("todo.add", result.intent)
        assertEquals("buy milk", result.slots["item"])
        // extractedText should be empty since there are no anonymous wildcards.
        assertEquals("", result.extractedText)
    }

    @Test
    fun named_slot_multiple() {
        val resolver = IntentResolver()
        resolver.register(
            "reminder.set",
            mapOf("en" to listOf("remind me to {task} at {time}")),
        )

        val result = resolver.resolve("remind me to buy milk at 5pm", "en")

        assertNotNull(result)
        assertEquals("buy milk", result.slots["task"])
        assertEquals("5pm", result.slots["time"])
        assertEquals("", result.extractedText)
    }

    @Test
    fun mixed_named_and_anonymous_slots() {
        val resolver = IntentResolver()
        resolver.register(
            "todo.add",
            mapOf("en" to listOf("add {item} to * list")),
        )

        val result = resolver.resolve("add milk to shopping list", "en")

        assertNotNull(result)
        assertEquals("milk", result.slots["item"])
        assertEquals("shopping", result.extractedText)
    }

    @Test
    fun named_slots_are_empty_for_star_only_patterns() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        val result = resolver.resolve("add milk to todo", "en")

        assertNotNull(result)
        assertEquals("milk", result.extractedText)
        assertEquals(emptyMap(), result.slots)
    }

    @Test
    fun named_slot_case_insensitive() {
        val resolver = IntentResolver()
        resolver.register(
            "search",
            mapOf("en" to listOf("search for {query}")),
        )

        val result = resolver.resolve("Search For kotlin multiplatform", "en")

        assertNotNull(result)
        assertEquals("kotlin multiplatform", result.slots["query"])
    }

    // ---- Fuzzy matching tests ----

    @Test
    fun fuzzy_matches_with_filler_words() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        // "please" is a filler word that breaks exact matching
        val result = resolver.resolve("please add milk to todo", "en", fuzzyThreshold = 0.6f)

        assertNotNull(result)
        assertEquals("todo.add", result.intent)
        assertTrue(result.confidence < 1.0f)
    }

    @Test
    fun fuzzy_returns_null_below_threshold() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        // "hello world" shares no tokens with "add * to todo"
        val result = resolver.resolve("hello world", "en", fuzzyThreshold = 0.6f)

        assertNull(result)
    }

    @Test
    fun fuzzy_disabled_when_threshold_is_zero() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        // This would fuzzy-match but threshold is 0
        val result = resolver.resolve("please add milk to todo", "en", fuzzyThreshold = 0.0f)

        assertNull(result)
    }

    @Test
    fun fuzzy_prefers_exact_match_over_fuzzy() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        // Exact match should win and confidence should be 1.0
        val result = resolver.resolve("add milk to todo", "en", fuzzyThreshold = 0.6f)

        assertNotNull(result)
        assertEquals("todo.add", result.intent)
        assertEquals(1.0f, result.confidence)
    }

    @Test
    fun fuzzy_picks_best_scoring_pattern() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))
        resolver.register("music.play", mapOf("en" to listOf("play * music")))

        // "add milk to my todo list" shares 3/3 literal tokens with "add * to todo"
        // and 0/1 with "play * music"
        val result = resolver.resolve("add milk to my todo list", "en", fuzzyThreshold = 0.5f)

        assertNotNull(result)
        assertEquals("todo.add", result.intent)
    }
}
