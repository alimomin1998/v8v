@file:OptIn(ExperimentalJsExport::class)

package io.v8v.core

import io.v8v.core.model.VoiceAgentConfig
import io.v8v.mcp.McpActionHandler
import io.v8v.mcp.McpClient
import io.v8v.mcp.McpServerConfig
import io.v8v.remote.WebhookActionHandler
import io.v8v.remote.WebhookConfig
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * JavaScript/TypeScript-friendly facade for the Voice Agent framework.
 *
 * Wraps the Kotlin [VoiceAgent] and converts coroutine-based Flows into
 * simple callback registrations that JS/TS developers expect.
 *
 * Usage from JavaScript:
 * ```js
 * const agent = new VoiceAgentJs('en');
 *
 * // LOCAL action
 * agent.registerPhrase('todo.add', 'en', 'add *');
 *
 * // MCP action (cross-app via local MCP server)
 * agent.registerMcpAction('task.create', 'en',
 *     ['create task *', 'new task *', 'task *'],
 *     'http://localhost:3001/mcp', 'create_task');
 *
 * // REMOTE action (webhook)
 * agent.registerWebhookAction('notify.team', 'en',
 *     ['notify *', 'send notification *', 'alert *'],
 *     'https://n8n.example.com/webhook/voice');
 *
 * agent.onTranscript(text => console.log('Heard:', text));
 * agent.onIntent((intent, text) => console.log(intent, text));
 * agent.onError(msg => console.error(msg));
 * agent.start();
 * ```
 */
@JsExport
class VoiceAgentJs(
    language: String = "en"
) {
    private val scope = MainScope()
    private val engine = WebSpeechEngine()
    private val agent =
        VoiceAgent(
            engine = engine,
            config = VoiceAgentConfig(language = language),
            // No permissionHelper — on the web, mic permission must be requested
            // from JavaScript in the user-gesture (click handler) context BEFORE
            // calling start(). Kotlin coroutines dispatch asynchronously, breaking
            // the gesture chain, which causes browsers to silently deny getUserMedia.
        )

    private val mcpClients = mutableListOf<McpClient>()
    private val webhookHandlers = mutableListOf<WebhookActionHandler>()
    private var errorCallback: ((String) -> Unit)? = null

    /**
     * Register a LOCAL voice command phrase pattern for an intent.
     *
     * @param intent Unique intent name (e.g. "todo.add").
     * @param language BCP-47 language tag (e.g. "en", "es").
     * @param phrase Pattern with `*` wildcards (e.g. "add * to list").
     */
    fun registerPhrase(
        intent: String,
        language: String,
        phrase: String
    ) {
        agent.registerAction(intent, mapOf(language to listOf(phrase))) { /* no-op local handler */ }
    }

    /**
     * Register an MCP action that calls a tool on a local MCP server via JSON-RPC 2.0.
     *
     * Uses the library's built-in Ktor-based [McpClient] for the HTTP call.
     * On JS, the Ktor engine uses the browser's `fetch()` API internally,
     * so CORS behaviour is identical to a manual fetch() call.
     *
     * @param intent Unique intent name (e.g. "task.create").
     * @param language BCP-47 language tag (e.g. "en").
     * @param phrases Array of patterns with `*` wildcards (e.g. ["create task *", "new task *"]).
     * @param serverUrl Full MCP server URL (e.g. "http://localhost:3001/mcp").
     * @param toolName The MCP tool to invoke (e.g. "create_task").
     */
    fun registerMcpAction(
        intent: String,
        language: String,
        phrases: Array<String>,
        serverUrl: String,
        toolName: String,
    ) {
        val config = McpServerConfig.fromUrl(name = "$intent-mcp", url = serverUrl)
        val client = McpClient(config)
        mcpClients.add(client)
        agent.registerAction(
            intent,
            mapOf(language to phrases.toList()),
            McpActionHandler(client, toolName),
        )
    }

    /**
     * Register a REMOTE webhook action that POSTs to a URL.
     *
     * Uses the library's built-in Ktor-based [WebhookActionHandler] for the HTTP call.
     *
     * @param intent Unique intent name (e.g. "notify.team").
     * @param language BCP-47 language tag (e.g. "en").
     * @param phrases Array of patterns with `*` wildcards (e.g. ["notify *", "alert *"]).
     * @param webhookUrl Full webhook URL (e.g. "https://n8n.example.com/webhook/voice").
     */
    fun registerWebhookAction(
        intent: String,
        language: String,
        phrases: Array<String>,
        webhookUrl: String,
    ) {
        val handler = WebhookActionHandler(WebhookConfig(url = webhookUrl))
        webhookHandlers.add(handler)
        agent.registerAction(
            intent,
            mapOf(language to phrases.toList()),
            handler,
        )
    }

    /**
     * Register a callback that fires whenever a transcript is received.
     *
     * @param callback Called with the transcribed text.
     */
    fun onTranscript(callback: (String) -> Unit) {
        scope.launch {
            agent.transcript.collect { callback(it) }
        }
    }

    /**
     * Register a callback that fires when an action completes successfully.
     *
     * For LOCAL actions, `message` is the extracted text (e.g. "buy milk").
     * For MCP actions, `message` is the server's response text.
     * For REMOTE actions, `message` is the webhook's response message.
     *
     * @param callback Called with `(intentName, message)`.
     */
    fun onIntent(callback: (String, String) -> Unit) {
        scope.launch {
            agent.actionResults.collect { result ->
                when (result) {
                    is ActionResult.Success -> callback(result.intent, result.message)
                    is ActionResult.Error -> {
                        errorCallback?.invoke("[${result.scope}] ${result.message}")
                    }
                }
            }
        }
    }

    /**
     * Register a callback that fires when an error occurs.
     *
     * Receives both engine/permission errors (from [VoiceAgentError]) and
     * action execution errors (from failed MCP/webhook calls).
     *
     * @param callback Called with the error message string.
     */
    fun onError(callback: (String) -> Unit) {
        errorCallback = callback
        scope.launch {
            agent.errors.collect { error -> callback(error.message) }
        }
    }

    /**
     * Register a callback for unhandled speech (no intent matched).
     *
     * @param callback Called with the raw unmatched text.
     */
    fun onUnhandled(callback: (String) -> Unit) {
        scope.launch {
            agent.unhandledText.collect { callback(it) }
        }
    }

    /** Start listening for speech. */
    fun start() {
        agent.start()
    }

    /** Stop listening. */
    fun stop() {
        agent.stop()
    }

    /**
     * Change the recognition language at runtime.
     *
     * @param language BCP-47 language tag.
     */
    fun updateLanguage(language: String) {
        agent.updateConfig(agent.config.copy(language = language))
    }

    /**
     * Enable or disable continuous listening mode.
     *
     * @param enabled `true` to keep listening after each utterance.
     */
    fun setContinuous(enabled: Boolean) {
        agent.updateConfig(agent.config.copy(continuous = enabled))
    }

    /**
     * Set the fuzzy matching threshold (0.0 = exact only, 1.0 = very fuzzy).
     *
     * @param threshold Value between 0.0 and 1.0.
     */
    fun setFuzzyThreshold(threshold: Float) {
        agent.updateConfig(agent.config.copy(fuzzyThreshold = threshold))
    }

    /** Release all resources. The agent should not be used after this call. */
    fun destroy() {
        agent.destroy()
        mcpClients.forEach { it.close() }
        mcpClients.clear()
        webhookHandlers.forEach { it.close() }
        webhookHandlers.clear()
        scope.cancel()
    }
}
