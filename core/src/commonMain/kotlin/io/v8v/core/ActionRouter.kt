package io.v8v.core

import io.v8v.core.model.ResolvedIntent

/**
 * Routes resolved intents to their registered [ActionHandler]s.
 *
 * Each intent string maps to exactly one handler. Registering
 * a handler for an intent that already has one replaces the previous handler.
 *
 * The router is scope-agnostic — it dispatches to whatever [ActionHandler]
 * was registered, whether that is a local lambda, an MCP client, or a
 * remote webhook.
 */
class ActionRouter {

    private val handlers = mutableMapOf<String, ActionHandler>()

    /**
     * Register an [ActionHandler] for the given intent.
     *
     * @param intent Intent identifier (e.g. "todo.add").
     * @param handler The handler that will execute when this intent is dispatched.
     */
    fun register(intent: String, handler: ActionHandler) {
        handlers[intent] = handler
    }

    /**
     * Dispatch a resolved intent to its registered handler.
     *
     * @return [ActionResult] from the handler, or [ActionResult.Error] if
     *   no handler is registered for the intent.
     */
    suspend fun dispatch(resolved: ResolvedIntent): ActionResult {
        val handler = handlers[resolved.intent]
            ?: return ActionResult.Error(
                scope = ActionScope.LOCAL,
                intent = resolved.intent,
                message = "No handler registered for intent '${resolved.intent}'",
            )
        return try {
            handler.execute(resolved)
        } catch (e: Exception) {
            ActionResult.Error(
                scope = handler.scope,
                intent = resolved.intent,
                message = e.message ?: "Action execution failed",
            )
        }
    }

    /** Check whether a handler exists for [intent]. */
    fun hasHandler(intent: String): Boolean = intent in handlers

    /** Get the scope of the handler for [intent], or null. */
    fun scopeOf(intent: String): ActionScope? = handlers[intent]?.scope

    /** Remove all registered handlers. */
    fun clear() {
        handlers.clear()
    }
}
