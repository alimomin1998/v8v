package io.v8v.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * JS-specific tests for [IntentResolver].
 *
 * These run in a headless browser (via Karma) and verify that the
 * regex-based intent resolution works correctly in the JS/IR target.
 */
class IntentResolverJsTest {

    @Test
    fun wildcard_pattern_works_in_js() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        val result = resolver.resolve("add buy milk to todo", "en")

        assertNotNull(result)
        assertEquals("todo.add", result.intent)
        assertEquals("buy milk", result.extractedText)
    }

    @Test
    fun named_slots_work_in_js() {
        val resolver = IntentResolver()
        resolver.register(
            "reminder.set",
            mapOf("en" to listOf("remind me to {task} at {time}")),
        )

        val result = resolver.resolve("remind me to buy milk at 5pm", "en")

        assertNotNull(result)
        assertEquals("buy milk", result.slots["task"])
        assertEquals("5pm", result.slots["time"])
    }

    @Test
    fun fuzzy_matching_works_in_js() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        val result = resolver.resolve("please add milk to todo", "en", fuzzyThreshold = 0.5f)

        assertNotNull(result)
        assertEquals("todo.add", result.intent)
    }

    @Test
    fun no_match_returns_null_in_js() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add * to todo")))

        val result = resolver.resolve("hello world", "en")

        assertNull(result)
    }

    @Test
    fun case_insensitive_matching_in_js() {
        val resolver = IntentResolver()
        resolver.register("todo.add", mapOf("en" to listOf("add *")))

        val result = resolver.resolve("ADD MILK", "en")

        assertNotNull(result)
        assertEquals("milk", result.extractedText)
    }
}
