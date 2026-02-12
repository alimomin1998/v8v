package io.v8v.core

import io.v8v.core.model.ResolvedIntent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActionRouterTest {

    private fun intent(name: String = "test.intent") = ResolvedIntent(
        intent = name,
        extractedText = "hello",
        rawText = "hello world",
        language = "en",
    )

    @Test
    fun dispatch_returns_success_for_local_handler() = runTest {
        val router = ActionRouter()
        var called = false
        router.register("test.intent", LocalActionHandler { called = true })

        val result = router.dispatch(intent())

        assertIs<ActionResult.Success>(result)
        assertEquals(ActionScope.LOCAL, result.scope)
        assertTrue(called)
    }

    @Test
    fun dispatch_returns_error_for_unregistered_intent() = runTest {
        val router = ActionRouter()

        val result = router.dispatch(intent("unknown"))

        assertIs<ActionResult.Error>(result)
        assertTrue(result.message.contains("No handler"))
    }

    @Test
    fun hasHandler_returns_correct_value() {
        val router = ActionRouter()
        router.register("test.intent", LocalActionHandler { })

        assertTrue(router.hasHandler("test.intent"))
        assertFalse(router.hasHandler("other"))
    }

    @Test
    fun scopeOf_returns_handler_scope() {
        val router = ActionRouter()
        router.register("local", LocalActionHandler { })

        assertEquals(ActionScope.LOCAL, router.scopeOf("local"))
        assertNull(router.scopeOf("missing"))
    }

    @Test
    fun clear_removes_all_handlers() = runTest {
        val router = ActionRouter()
        router.register("a", LocalActionHandler { })
        router.register("b", LocalActionHandler { })
        router.clear()

        assertFalse(router.hasHandler("a"))
        assertFalse(router.hasHandler("b"))
    }

    @Test
    fun dispatch_catches_handler_exception() = runTest {
        val router = ActionRouter()
        router.register("fail", LocalActionHandler { error("boom") })

        val result = router.dispatch(intent("fail"))

        assertIs<ActionResult.Error>(result)
        assertTrue(result.message.contains("boom"))
    }

    @Test
    fun replacing_handler_uses_new_one() = runTest {
        val router = ActionRouter()
        var first = false
        var second = false
        router.register("x", LocalActionHandler { first = true })
        router.register("x", LocalActionHandler { second = true })

        router.dispatch(intent("x"))

        assertFalse(first)
        assertTrue(second)
    }
}
