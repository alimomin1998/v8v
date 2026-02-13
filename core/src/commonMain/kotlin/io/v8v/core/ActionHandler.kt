package io.v8v.core

import io.v8v.core.model.ResolvedIntent

/**
 * Abstraction for executing a resolved voice intent.
 *
 * Implementations decide *how* and *where* the action runs:
 * - [LocalActionHandler] — in-app lambda (default)
 * - MCP handler — cross-app via local MCP server
 * - Webhook handler — remote via n8n or similar
 *
 * The [VoiceAgent] dispatches through this interface, so the
 * developer's registration code is transport-agnostic.
 */
interface ActionHandler {
    /** Where this handler executes. */
    val scope: ActionScope

    /**
     * Execute the action for the given resolved intent.
     *
     * @return [ActionResult.Success] or [ActionResult.Error].
     */
    suspend fun execute(intent: ResolvedIntent): ActionResult
}
