package io.v8v.core

import io.v8v.core.model.ResolvedIntent
import io.v8v.core.model.VoiceAgentConfig
import io.v8v.mcp.McpActionHandler
import io.v8v.mcp.McpClient
import io.v8v.mcp.McpServerConfig
import io.v8v.remote.WebhookActionHandler
import io.v8v.remote.WebhookConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Callback-based facade for [VoiceAgent] that provides a **uniform API
 * across all platforms** — Android, iOS, macOS, and Web.
 *
 * Instead of collecting Kotlin Flows directly, consumers register simple
 * callbacks. This makes usage identical regardless of the host language:
 *
 * **Kotlin (Android):**
 * ```kotlin
 * val agent = VoiceAgentCallbacks(engine, config)
 * agent.onTranscript { text -> println("Heard: $text") }
 * agent.onIntent { intent, message -> println("$intent: $message") }
 * agent.onError { msg -> println("Error: $msg") }
 * agent.registerLocalAction("todo.add", mapOf("en" to listOf("add *"))) { resolved ->
 *     addTodo(resolved.extractedText)
 * }
 * agent.start()
 * ```
 *
 * **Swift (iOS / macOS):**
 * ```swift
 * let agent = VoiceAgentCallbacks(engine: engine, config: config)
 * agent.onTranscript { text in print("Heard: \(text)") }
 * agent.onIntent { intent, message in print("\(intent): \(message)") }
 * agent.onError { msg in print("Error: \(msg)") }
 * agent.start()
 * ```
 *
 * **JavaScript (Web) — via VoiceAgentJs:**
 * ```javascript
 * agent.onTranscript(text => console.log('Heard:', text));
 * agent.onIntent((intent, message) => console.log(intent, message));
 * agent.onError(msg => console.error(msg));
 * agent.start();
 * ```
 */
class VoiceAgentCallbacks(
    engine: SpeechRecognitionEngine,
    config: VoiceAgentConfig = VoiceAgentConfig(),
    permissionHelper: PermissionHelper? = null,
) {
    private val agent = VoiceAgent(engine, config, permissionHelper)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mcpClients = mutableListOf<McpClient>()
    private val webhookHandlers = mutableListOf<WebhookActionHandler>()
    private var errorCallback: ((String) -> Unit)? = null

    // ── Callback registration ──

    /** Called with each final (and optionally partial) transcript. */
    fun onTranscript(callback: (String) -> Unit) {
        scope.launch {
            agent.transcript.collect { callback(it) }
        }
    }

    /**
     * Called when an action completes successfully.
     *
     * For LOCAL actions, [message] is the extracted text (e.g. "buy milk").
     * For MCP actions, [message] is the server's response text.
     * For REMOTE actions, [message] is the webhook's response message.
     *
     * Action errors are forwarded to [onError] instead of this callback.
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
     * Called when an error occurs.
     *
     * Receives both engine/permission errors and action execution errors
     * (failed MCP/webhook calls). Register this **before** [onIntent] to
     * ensure action errors are forwarded.
     */
    fun onError(callback: (String) -> Unit) {
        errorCallback = callback
        scope.launch {
            agent.errors.collect { error -> callback(error.message) }
        }
    }

    /** Called when speech doesn't match any registered intent. */
    fun onUnhandled(callback: (String) -> Unit) {
        scope.launch {
            agent.unhandledText.collect { callback(it) }
        }
    }

    /**
     * Called with every action result (success or error).
     *
     * This is the advanced version of [onIntent] — it gives you the full
     * [ActionResult] object including scope and data map.
     */
    fun onActionResult(callback: (ActionResult) -> Unit) {
        scope.launch {
            agent.actionResults.collect { callback(it) }
        }
    }

    /** Called when the agent state changes (IDLE, LISTENING, PROCESSING). */
    fun onStateChange(callback: (String) -> Unit) {
        scope.launch {
            agent.state.collect { callback(it.name) }
        }
    }

    /** Called with the audio level (0.0–1.0) while listening. */
    fun onAudioLevel(callback: (Float) -> Unit) {
        scope.launch {
            agent.audioLevel.collect { callback(it) }
        }
    }

    // ── Action registration ──

    /** Register a LOCAL voice action with a callback handler. */
    fun registerLocalAction(
        intent: String,
        phrases: Map<String, List<String>>,
        handler: (ResolvedIntent) -> Unit,
    ) {
        agent.registerAction(intent, phrases, handler)
    }

    /** Register an MCP action that calls a tool on a local MCP server. */
    fun registerMcpAction(
        intent: String,
        phrases: Map<String, List<String>>,
        serverUrl: String,
        toolName: String,
    ) {
        val config = McpServerConfig.fromUrl(name = "$intent-mcp", url = serverUrl)
        val client = McpClient(config)
        mcpClients.add(client)
        agent.registerAction(intent, phrases, McpActionHandler(client, toolName))
    }

    /** Register a REMOTE webhook action that POSTs to a URL. */
    fun registerWebhookAction(
        intent: String,
        phrases: Map<String, List<String>>,
        webhookUrl: String,
    ) {
        val handler = WebhookActionHandler(WebhookConfig(url = webhookUrl))
        webhookHandlers.add(handler)
        agent.registerAction(intent, phrases, handler)
    }

    /** Register a voice action with an explicit [ActionHandler]. */
    fun registerActionWithHandler(
        intent: String,
        phrases: Map<String, List<String>>,
        handler: ActionHandler,
    ) {
        agent.registerAction(intent, phrases, handler)
    }

    // ── Control ──

    /** Start listening for speech. */
    fun start() {
        agent.start()
    }

    /** Stop listening. */
    fun stop() {
        agent.stop()
    }

    /** Update configuration at runtime. */
    fun updateConfig(newConfig: VoiceAgentConfig) {
        agent.updateConfig(newConfig)
    }

    /** Release all resources. */
    fun destroy() {
        agent.destroy()
        mcpClients.forEach { it.close() }
        mcpClients.clear()
        webhookHandlers.forEach { it.close() }
        webhookHandlers.clear()
        scope.cancel()
    }
}
