package io.v8v.core

import io.v8v.core.model.ResolvedIntent

/**
 * An [ActionHandler] that wraps a simple lambda for in-app execution.
 *
 * This is the default handler created when using the convenience
 * `VoiceAgent.registerAction(intent, phrases) { ... }` overload,
 * providing full backward compatibility with the original API.
 */
class LocalActionHandler(
    private val block: (ResolvedIntent) -> Unit,
) : ActionHandler {

    override val scope: ActionScope = ActionScope.LOCAL

    override suspend fun execute(intent: ResolvedIntent): ActionResult {
        return try {
            block(intent)
            ActionResult.Success(
                scope = ActionScope.LOCAL,
                intent = intent.intent,
            )
        } catch (e: Exception) {
            ActionResult.Error(
                scope = ActionScope.LOCAL,
                intent = intent.intent,
                message = e.message ?: "Local action failed",
            )
        }
    }
}
