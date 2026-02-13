package io.v8v.mcp

import io.v8v.core.ActionHandler
import io.v8v.core.ActionResult
import io.v8v.core.ActionScope
import io.v8v.core.model.ResolvedIntent
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * An [ActionHandler] that dispatches intents to a local MCP server.
 *
 * When the voice agent resolves an intent, this handler calls the
 * specified tool on the MCP server with the extracted text as an argument.
 *
 * @param client The [McpClient] connected to the target app's MCP server.
 * @param toolName The MCP tool to invoke (e.g. "create_task").
 * @param argumentKey The JSON key used for the extracted text. Defaults to "text".
 */
class McpActionHandler(
    private val client: McpClient,
    private val toolName: String,
    private val argumentKey: String = "text",
) : ActionHandler {
    override val scope: ActionScope = ActionScope.MCP

    override suspend fun execute(intent: ResolvedIntent): ActionResult =
        try {
            val arguments =
                buildJsonObject {
                    put(argumentKey, JsonPrimitive(intent.extractedText))
                    put("rawText", JsonPrimitive(intent.rawText))
                    put("language", JsonPrimitive(intent.language))
                }

            val result = client.callTool(toolName, arguments)

            if (result.isError) {
                ActionResult.Error(
                    scope = ActionScope.MCP,
                    intent = intent.intent,
                    message = result.content.firstOrNull()?.text ?: "MCP tool returned error",
                )
            } else {
                ActionResult.Success(
                    scope = ActionScope.MCP,
                    intent = intent.intent,
                    message = result.content.firstOrNull()?.text ?: "OK",
                    data = mapOf("server" to client.toString(), "tool" to toolName),
                )
            }
        } catch (e: Exception) {
            ActionResult.Error(
                scope = ActionScope.MCP,
                intent = intent.intent,
                message = "MCP call failed: ${e.message}",
            )
        }
}
