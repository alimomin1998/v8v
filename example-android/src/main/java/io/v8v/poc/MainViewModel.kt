@file:Suppress("ktlint:standard:max-line-length")

package io.v8v.poc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.v8v.core.AgentState
import io.v8v.core.VoiceAgentCallbacks
import io.v8v.core.createPlatformEngine
import io.v8v.core.model.VoiceAgentConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "V8V-Example"
        const val MCP_PORT = 3001
    }

    // ---- Observable state (Compose collects these) ----

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

    private val _agentState = MutableStateFlow(AgentState.IDLE)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // ---- Settings state ----

    private val _language = MutableStateFlow(VoiceAgentConfig().language)
    val language: StateFlow<String> = _language.asStateFlow()

    private val _continuous = MutableStateFlow(VoiceAgentConfig().continuous)
    val continuous: StateFlow<Boolean> = _continuous.asStateFlow()

    private val _fuzzyThreshold = MutableStateFlow(VoiceAgentConfig().fuzzyThreshold)
    val fuzzyThreshold: StateFlow<Float> = _fuzzyThreshold.asStateFlow()

    // ---- Infrastructure ----

    private val mockMcpServer = MockMcpServer(MCP_PORT)

    /**
     * Uses VoiceAgentCallbacks — the same callback-based API as
     * iOS/macOS (Swift) and Web (VoiceAgentJs).
     */
    private val agent =
        VoiceAgentCallbacks(
            engine = createPlatformEngine(application.applicationContext),
            config = VoiceAgentConfig(),
        )

    init {
        mockMcpServer.start()

        // ── Register callbacks (same pattern as iOS/macOS/Web) ──

        agent.onTranscript { text ->
            _lastTranscript.value = text
            appendLog("Transcript='$text'")
        }

        agent.onError { msg ->
            _lastError.value = msg
            appendLog("Error: $msg")
        }

        agent.onIntent { intent, message ->
            _lastError.value = ""
            appendLog("[${scopeOf(intent)}] $intent: $message")
        }

        agent.onUnhandled { text ->
            appendLog("Unmatched='$text'")
        }

        agent.onStateChange { state ->
            _agentState.value = AgentState.valueOf(state)
        }

        agent.onAudioLevel { level ->
            _audioLevel.value = level
        }

        // ── Register actions (same pattern as iOS/macOS/Web) ──

        // LOCAL: "add <item>" → in-app todo list
        agent.registerLocalAction(
            intent = "todo.add",
            phrases =
                mapOf(
                    "en" to listOf("add *", "add * to todo", "add * to my list"),
                    "hi" to listOf("* todo mein add karo", "todo mein * add karo"),
                ),
        ) { resolved ->
            _todos.value = _todos.value + resolved.extractedText
            _lastTranscript.value = resolved.rawText
        }

        // MCP: "create task <item>" → local MCP server
        agent.registerMcpAction(
            intent = "task.create",
            phrases = mapOf("en" to listOf("create task *", "new task *", "task *")),
            serverUrl = "http://127.0.0.1:$MCP_PORT/mcp",
            toolName = "create_task",
        )

        // REMOTE: "notify <message>" → webhook (URL set later via UI)
        // Registered when user enters a webhook URL in settings.

        viewModelScope.launch {
            appendLog("[MCP] Mock server running on port $MCP_PORT")
        }
    }

    fun startListening() = agent.start()

    fun stopListening() = agent.stop()

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

    fun removeTodo(index: Int) {
        _todos.value =
            _todos.value.toMutableList().apply {
                if (index in indices) removeAt(index)
            }
    }

    fun setWebhookUrl(url: String) {
        _webhookUrl.value = url
        if (url.isNotBlank()) {
            agent.registerWebhookAction(
                intent = "notify.team",
                phrases = mapOf("en" to listOf("notify *", "send notification *", "alert *")),
                webhookUrl = url,
            )
            appendLog("[REMOTE] Webhook URL set: $url")
        }
    }

    override fun onCleared() {
        super.onCleared()
        agent.destroy()
        mockMcpServer.stop()
    }

    private fun applyConfig() {
        agent.updateConfig(
            VoiceAgentConfig(
                language = _language.value,
                continuous = _continuous.value,
                fuzzyThreshold = _fuzzyThreshold.value,
            ),
        )
    }

    private fun appendLog(line: String) {
        _debugLog.value = (_debugLog.value + line).takeLast(20)
    }

    private fun scopeOf(intent: String): String =
        when {
            intent.startsWith("todo.") -> "LOCAL"
            intent.startsWith("task.") -> "MCP"
            intent.startsWith("notify.") -> "REMOTE"
            else -> "LOCAL"
        }
}
