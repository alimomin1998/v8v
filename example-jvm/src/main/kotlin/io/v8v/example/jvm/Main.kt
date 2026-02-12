package io.v8v.example.jvm

import io.v8v.core.*
import io.v8v.core.model.ResolvedIntent
import io.v8v.core.model.VoiceAgentConfig
import io.v8v.mcp.McpActionHandler
import io.v8v.mcp.McpClient
import io.v8v.mcp.McpServerConfig
import io.v8v.remote.WebhookActionHandler
import io.v8v.remote.WebhookConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Voice Agent — JVM CLI Example
 *
 * Demonstrates the Voice Agent framework on JVM desktop. Since JVM has no
 * built-in speech recognition, this example uses a **text-based STT simulator**
 * that reads typed input from the console as if it were spoken.
 *
 * This proves that the core framework, MCP connector, and Remote connector
 * all work on JVM — you just need to provide your own SpeechRecognitionEngine
 * (e.g., backed by Vosk, Whisper.cpp, or a cloud STT API).
 *
 * Usage:
 *   ./gradlew :example-jvm:run
 *
 * Then type commands like:
 *   > add buy milk
 *   > create task fix the bug
 *   > notify server is down
 *   > list todos
 *   > quit
 *
 * For MCP to work, start the standalone MCP server first:
 *   node example-mcp-server/server.js
 */
fun main() = runBlocking {
    println("═══════════════════════════════════════════════════════")
    println("  Voice Agent — JVM CLI Demo")
    println("  Type commands as if speaking. Type 'quit' to exit.")
    println("═══════════════════════════════════════════════════════")
    println()

    // ── Setup ───────────────────────────────────────────────────

    val engine = ConsoleSimulatedEngine()
    val agent = VoiceAgent(
        engine = engine,
        config = VoiceAgentConfig(language = "en"),
    )

    val todos = mutableListOf<String>()

    // ── LOCAL: "add <item>" ─────────────────────────────────────

    agent.registerAction(
        intent = "todo.add",
        phrases = mapOf("en" to listOf("add *", "add * to todo", "add * to my list")),
    ) { resolved ->
        todos.add(resolved.extractedText)
        println("  [LOCAL] Added: \"${resolved.extractedText}\" (total: ${todos.size})")
    }

    agent.registerAction(
        intent = "todo.remove",
        phrases = mapOf("en" to listOf("remove *", "delete *")),
    ) { resolved ->
        val idx = todos.indexOfFirst { it.contains(resolved.extractedText, ignoreCase = true) }
        if (idx >= 0) {
            val removed = todos.removeAt(idx)
            println("  [LOCAL] Removed: \"$removed\" (remaining: ${todos.size})")
        } else {
            println("  [LOCAL] \"${resolved.extractedText}\" not found")
        }
    }

    agent.registerAction(
        intent = "todo.list",
        phrases = mapOf("en" to listOf("list todos", "show todos", "show my list")),
    ) { _ ->
        if (todos.isEmpty()) {
            println("  [LOCAL] Todo list is empty")
        } else {
            println("  [LOCAL] Todos:")
            todos.forEachIndexed { i, t -> println("    $i: $t") }
        }
    }

    // ── MCP: "create task <item>" ───────────────────────────────

    val mcpClient = McpClient(McpServerConfig(name = "task-server", port = 3001))
    var mcpConnected = false

    try {
        val info = mcpClient.initialize()
        mcpConnected = true
        println("[MCP] Connected to ${info.serverInfo?.name ?: "server"}")
    } catch (e: Exception) {
        println("[MCP] Not connected (${e.message}). Start: node example-mcp-server/server.js")
    }

    agent.registerAction(
        intent = "task.create",
        phrases = mapOf("en" to listOf("create task *", "new task *")),
        handler = McpActionHandler(mcpClient, "create_task"),
    )

    // ── REMOTE: "notify <message>" ──────────────────────────────

    agent.registerAction(
        intent = "notify.team",
        phrases = mapOf("en" to listOf("notify *", "send notification *", "alert *")),
        handler = WebhookActionHandler(
            WebhookConfig(url = "https://httpbin.org/post"),
        ),
    )

    // ── Collect flows ───────────────────────────────────────────

    launch {
        agent.errors.collect { error ->
            println("  [ERROR] ${error.message}")
        }
    }

    launch {
        agent.actionResults.collect { result ->
            when (result) {
                is ActionResult.Success -> {
                    println("  [${result.scope}] ${result.intent}: ${result.message}")
                }
                is ActionResult.Error -> {
                    println("  [${result.scope}] ${result.intent} FAILED: ${result.message}")
                }
            }
        }
    }

    launch {
        agent.unhandledText.collect { text ->
            println("  [UNMATCHED] \"$text\" — try: add/remove/list/create task/notify")
        }
    }

    // ── Start agent + read input ────────────────────────────────

    agent.start()

    println()
    println("Ready! Type a voice command:")
    println("  Examples: 'add buy milk', 'create task fix bug', 'notify server down', 'list todos'")
    println()

    // Read lines from console and feed them to the simulated engine
    withContext(Dispatchers.IO) {
        while (isActive) {
            print("> ")
            val line = readlnOrNull()?.trim() ?: break
            if (line.equals("quit", ignoreCase = true) || line.equals("exit", ignoreCase = true)) {
                break
            }
            if (line.isNotEmpty()) {
                engine.simulateInput(line)
                delay(100) // Give time for flow processing
            }
        }
    }

    // ── Cleanup ─────────────────────────────────────────────────

    agent.destroy()
    mcpClient.close()
    println("\nGoodbye!")
}

/**
 * A simulated SpeechRecognitionEngine for JVM that accepts typed text
 * input as "speech". Useful for testing the full agent pipeline without
 * a real microphone.
 *
 * In a real JVM app, replace this with an engine backed by Vosk,
 * Whisper.cpp, Google Cloud STT, or similar.
 */
class ConsoleSimulatedEngine : SpeechRecognitionEngine {

    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SpeechEvent> = _events.asSharedFlow()

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    override fun startListening(language: String) {
        _isListening.value = true
        _events.tryEmit(SpeechEvent.ReadyForSpeech)
    }

    override fun stopListening() {
        _isListening.value = false
        _events.tryEmit(SpeechEvent.EndOfSpeech)
    }

    override fun destroy() {
        stopListening()
    }

    /**
     * Simulate speech input. Call this with typed text to feed it
     * through the agent as if it were a speech transcript.
     */
    fun simulateInput(text: String) {
        _events.tryEmit(SpeechEvent.FinalResult(text))
    }
}
