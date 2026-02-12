package io.v8v.example

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.v8v.core.ActionResult
import io.v8v.core.ActionScope
import io.v8v.core.AgentState
import io.v8v.core.VoiceAgent
import io.v8v.core.VoiceAgentError
import io.v8v.core.createPlatformEngine
import io.v8v.core.model.VoiceAgentConfig
import io.v8v.mcp.McpActionHandler
import io.v8v.mcp.McpClient
import io.v8v.mcp.McpServerConfig
import io.v8v.remote.WebhookActionHandler
import io.v8v.remote.WebhookConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel demonstrating all three action scopes:
 *
 * 1. LOCAL  — "add <item>" → in-app todo list
 * 2. MCP    — "create task <item>" → local MCP server (mock)
 * 3. REMOTE — "notify <message>" → n8n webhook (configurable URL)
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "VoiceTodoDemo"
        const val MCP_PORT = 3001
    }

    // ---- Observable state ----

    private val _todos = MutableStateFlow<List<String>>(emptyList())
    val todos: StateFlow<List<String>> = _todos.asStateFlow()

    private val _lastTranscript = MutableStateFlow("")
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError.asStateFlow()

    private val _debugLog = MutableStateFlow<List<String>>(emptyList())
    val debugLog: StateFlow<List<String>> = _debugLog.asStateFlow()

    private val _webhookUrl = MutableStateFlow("")
    val webhookUrl: StateFlow<String> = _webhookUrl.asStateFlow()

    // ---- Settings state ----

    private val _language = MutableStateFlow(VoiceAgentConfig().language)
    val language: StateFlow<String> = _language.asStateFlow()

    private val _continuous = MutableStateFlow(VoiceAgentConfig().continuous)
    val continuous: StateFlow<Boolean> = _continuous.asStateFlow()

    private val _fuzzyThreshold = MutableStateFlow(VoiceAgentConfig().fuzzyThreshold)
    val fuzzyThreshold: StateFlow<Float> = _fuzzyThreshold.asStateFlow()

    // ---- Infrastructure ----

    private val mockMcpServer = MockMcpServer(MCP_PORT)
    private val engine = createPlatformEngine(application.applicationContext)

    private val voiceAgent = VoiceAgent(
        engine = engine,
        config = VoiceAgentConfig(),
    )

    val agentState: StateFlow<AgentState> = voiceAgent.state
    val audioLevel: StateFlow<Float> = voiceAgent.audioLevel

    // MCP client
    private val mcpClient = McpClient(
        McpServerConfig(name = "mock-task-app", port = MCP_PORT),
    )

    // Webhook handler (created lazily when URL is set)
    private var webhookHandler: WebhookActionHandler? = null

    init {
        // Start the embedded mock MCP server
        mockMcpServer.start()

        // Initialize MCP session in background
        viewModelScope.launch {
            try {
                val info = mcpClient.initialize()
                appendLog("[MCP] Connected to ${info.serverInfo?.name ?: "server"}")
                Log.d(TAG, "MCP initialized: $info")
            } catch (e: Exception) {
                appendLog("[MCP] Init failed: ${e.message}")
                Log.e(TAG, "MCP init failed", e)
            }
        }

        // ---- 1. LOCAL: "add <item>" → in-app todo list ----
        voiceAgent.registerAction(
            intent = "todo.add",
            phrases = mapOf(
                "en" to listOf(
                    "add *",
                    "add * to todo",
                    "add * to do",
                    "add * todo",
                    "todo *",
                    "add * to my list",
                    "add * to the list",
                ),
                "hi" to listOf(
                    "* todo mein add karo",
                    "todo mein * add karo",
                    "* list mein add karo",
                ),
            ),
        ) { resolved ->
            _todos.value = _todos.value + resolved.extractedText
            _lastTranscript.value = resolved.rawText
        }

        // ---- 2. MCP: "create task <item>" → local MCP server ----
        voiceAgent.registerAction(
            intent = "task.create",
            phrases = mapOf(
                "en" to listOf(
                    "create task *",
                    "new task *",
                    "task *",
                ),
            ),
            handler = McpActionHandler(mcpClient, "create_task"),
        )

        // ---- 3. REMOTE: "notify <message>" → n8n webhook ----
        // Registered with a placeholder; actual webhook handler is swapped
        // when the user sets a URL via setWebhookUrl().
        voiceAgent.registerAction(
            intent = "notify.team",
            phrases = mapOf(
                "en" to listOf(
                    "notify *",
                    "send notification *",
                    "alert *",
                ),
            ),
            handler = WebhookActionHandler(
                WebhookConfig(url = "http://localhost/placeholder"),
            ),
        )

        // ---- Collect flows ----

        viewModelScope.launch {
            voiceAgent.transcript.collect { transcript ->
                _lastTranscript.value = transcript
                appendLog("Transcript='$transcript'")
                Log.d(TAG, "Transcript='$transcript'")
            }
        }

        viewModelScope.launch {
            voiceAgent.unhandledText.collect { text ->
                appendLog("Unmatched='$text'")
                Log.w(TAG, "Unmatched='$text'")
            }
        }

        viewModelScope.launch {
            voiceAgent.errors.collect { error ->
                _lastError.value = error.message
                appendLog("Error: ${error.message}")
                Log.e(TAG, "VoiceAgent error: $error")
            }
        }

        viewModelScope.launch {
            voiceAgent.actionResults.collect { result ->
                when (result) {
                    is ActionResult.Success -> {
                        _lastError.value = "" // clear error on success
                        val badge = scopeBadge(result.scope)
                        appendLog("$badge ${result.intent}: ${result.message}")
                        Log.d(TAG, "$badge Success: ${result.intent} → ${result.message}")
                    }
                    is ActionResult.Error -> {
                        val badge = scopeBadge(result.scope)
                        appendLog("$badge ${result.intent} FAILED: ${result.message}")
                        Log.e(TAG, "$badge Error: ${result.intent} → ${result.message}")
                    }
                }
            }
        }
    }

    fun startListening() { voiceAgent.start() }
    fun stopListening() { voiceAgent.stop() }

    fun setLanguage(lang: String) {
        _language.value = lang
        applyConfig()
    }

    fun setContinuous(enabled: Boolean) {
        _continuous.value = enabled
        applyConfig()
    }

    fun setFuzzyThreshold(value: Float) {
        _fuzzyThreshold.value = value
        applyConfig()
    }

    private fun applyConfig() {
        val newConfig = VoiceAgentConfig(
            language = _language.value,
            continuous = _continuous.value,
            fuzzyThreshold = _fuzzyThreshold.value,
        )
        voiceAgent.updateConfig(newConfig)
        appendLog("[CONFIG] lang=${newConfig.language}, continuous=${newConfig.continuous}, fuzzy=${newConfig.fuzzyThreshold}")
    }

    fun removeTodo(index: Int) {
        _todos.value = _todos.value.toMutableList().apply {
            if (index in indices) removeAt(index)
        }
    }

    /**
     * Update the webhook URL for the remote (n8n) action.
     * Re-registers the "notify.team" intent with the new URL.
     */
    fun setWebhookUrl(url: String) {
        _webhookUrl.value = url
        if (url.isNotBlank()) {
            webhookHandler?.close()
            webhookHandler = WebhookActionHandler(WebhookConfig(url = url))
            voiceAgent.registerAction(
                intent = "notify.team",
                phrases = mapOf(
                    "en" to listOf("notify *", "send notification *", "alert *"),
                ),
                handler = webhookHandler!!,
            )
            appendLog("[REMOTE] Webhook URL set: $url")
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceAgent.destroy()
        mcpClient.close()
        webhookHandler?.close()
        mockMcpServer.stop()
    }

    private fun appendLog(line: String) {
        _debugLog.value = (_debugLog.value + line).takeLast(12)
    }

    private fun scopeBadge(scope: ActionScope): String = when (scope) {
        ActionScope.LOCAL -> "[LOCAL]"
        ActionScope.MCP -> "[MCP]"
        ActionScope.REMOTE -> "[REMOTE]"
    }
}
