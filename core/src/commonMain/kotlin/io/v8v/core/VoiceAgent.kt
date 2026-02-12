package io.v8v.core

import io.v8v.core.model.ResolvedIntent
import io.v8v.core.model.VoiceAgentConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State of the [VoiceAgent] lifecycle.
 */
enum class AgentState {
    /** Not listening. Waiting for [VoiceAgent.start]. */
    IDLE,
    /** Actively listening for speech via the engine. */
    LISTENING,
    /** Processing a transcript through intent resolution. */
    PROCESSING,
}

/**
 * Main entry point for the voice-agent framework.
 *
 * [VoiceAgent] wires together a [SpeechRecognitionEngine], an [IntentResolver],
 * and an [ActionRouter] to convert spoken language into app actions.
 *
 * Actions can execute locally (in-app lambda), via MCP (cross-app on-device),
 * or remotely (n8n webhook). The developer registers an [ActionHandler] for
 * each intent and the router dispatches transparently.
 *
 * Usage (local):
 * ```kotlin
 * agent.registerAction(
 *     intent = "todo.add",
 *     phrases = mapOf("en" to listOf("add * to todo")),
 * ) { resolved -> addTodo(resolved.extractedText) }
 * ```
 *
 * Usage (MCP / remote):
 * ```kotlin
 * agent.registerAction(
 *     intent = "task.create",
 *     phrases = mapOf("en" to listOf("create task *")),
 *     handler = McpActionHandler(mcpClient, "create_task"),
 * )
 * ```
 *
 * @param engine Platform-specific speech recognition engine.
 * @param config Agent configuration (language, continuous mode, etc.).
 * @param permissionHelper Optional platform permission helper. When provided,
 *   [start] checks microphone permission before listening and emits an error
 *   if permission is denied.
 */
class VoiceAgent(
    private val engine: SpeechRecognitionEngine,
    config: VoiceAgentConfig = VoiceAgentConfig(),
    private val permissionHelper: PermissionHelper? = null,
) {
    private var _config: VoiceAgentConfig = config

    /** Current agent configuration. */
    val config: VoiceAgentConfig get() = _config

    /**
     * Update the agent configuration at runtime.
     *
     * If the agent is currently listening and the language changed,
     * the speech engine is automatically restarted with the new language.
     * Changes to [VoiceAgentConfig.continuous] and [VoiceAgentConfig.partialResults]
     * take effect on the next utterance cycle without a restart.
     *
     * @param newConfig The new configuration to apply.
     */
    fun updateConfig(newConfig: VoiceAgentConfig) {
        val wasListening = _state.value == AgentState.LISTENING
        val languageChanged = _config.language != newConfig.language
        _config = newConfig
        if (wasListening && languageChanged) {
            engine.stopListening()
            engine.startListening(newConfig.language)
        }
    }
    private val intentResolver = IntentResolver()
    private val actionRouter = ActionRouter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AgentState.IDLE)
    /** Current agent state. */
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private val _transcript = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Emits every final (and optionally partial) transcript received from the engine. */
    val transcript: SharedFlow<String> = _transcript.asSharedFlow()

    private val _unhandledText = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Emits transcripts that did not match any registered intent. */
    val unhandledText: SharedFlow<String> = _unhandledText.asSharedFlow()

    private val _errors = MutableSharedFlow<VoiceAgentError>(extraBufferCapacity = 16)
    /** Emits structured errors for permission, engine, and action failures. */
    val errors: SharedFlow<VoiceAgentError> = _errors.asSharedFlow()

    private val _actionResults = MutableSharedFlow<ActionResult>(extraBufferCapacity = 16)
    /** Emits the [ActionResult] from every dispatched action (success or error). */
    val actionResults: SharedFlow<ActionResult> = _actionResults.asSharedFlow()

    private val _audioLevel = MutableStateFlow(0f)
    /**
     * Normalized audio input level (0.0–1.0) updated in real-time while listening.
     *
     * Suitable for driving a mic volume indicator / animation. The value is
     * derived from the platform's raw RMS dB reading and clamped to [0, 1].
     * Resets to 0 when the agent stops listening.
     */
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private var listeningJob: Job? = null

    /**
     * Register a local voice action using a simple lambda (backward-compatible API).
     *
     * The lambda is wrapped in a [LocalActionHandler] automatically.
     *
     * @param intent Unique intent identifier (e.g. "todo.add").
     * @param phrases Map of language code to phrase patterns.
     * @param handler Called when spoken text matches this intent.
     */
    fun registerAction(
        intent: String,
        phrases: Map<String, List<String>>,
        handler: (ResolvedIntent) -> Unit,
    ) {
        registerAction(intent, phrases, LocalActionHandler(handler))
    }

    /**
     * Register a voice action with an explicit [ActionHandler].
     *
     * Use this overload for MCP, webhook, or custom handlers.
     *
     * @param intent Unique intent identifier (e.g. "task.create").
     * @param phrases Map of language code to phrase patterns.
     * @param handler The [ActionHandler] that executes this intent.
     */
    fun registerAction(
        intent: String,
        phrases: Map<String, List<String>>,
        handler: ActionHandler,
    ) {
        intentResolver.register(intent, phrases)
        actionRouter.register(intent, handler)
    }

    /**
     * Start listening for speech. Events from the engine are collected,
     * resolved into intents, and dispatched to registered handlers.
     *
     * If a [PermissionHelper] was provided, microphone permission is checked
     * first. If denied, an error is emitted and listening does not start.
     *
     * In continuous mode, listening auto-restarts after each utterance.
     */
    fun start() {
        if (listeningJob?.isActive == true) return

        listeningJob = scope.launch {
            // Check permission if a helper is available.
            if (permissionHelper != null) {
                val status = permissionHelper.checkMicrophonePermission()
                if (status != PermissionStatus.GRANTED) {
                    _errors.emit(VoiceAgentError.PermissionDenied(status))
                    _state.value = AgentState.IDLE
                    return@launch
                }
            }

            // Start collecting engine events (before startListening so we don't miss events).
            engine.startListening(_config.language)
            _state.value = AgentState.LISTENING

            engine.events.collect { event ->
                handleEvent(event)
            }
        }
    }

    /** Stop listening. The agent moves to [AgentState.IDLE]. */
    fun stop() {
        engine.stopListening()
        listeningJob?.cancel()
        listeningJob = null
        _state.value = AgentState.IDLE
        _audioLevel.value = 0f
    }

    /**
     * Stop listening and release all resources.
     * The agent should not be used after this call.
     */
    fun destroy() {
        stop()
        scope.cancel()
        engine.destroy()
    }

    // ---- internal event handling ----

    private suspend fun handleEvent(event: SpeechEvent) {
        when (event) {
            is SpeechEvent.FinalResult -> {
                _state.value = AgentState.PROCESSING
                _transcript.emit(event.text)

                val resolved = intentResolver.resolve(event.text, _config.language, _config.fuzzyThreshold)
                if (resolved != null) {
                    val result = actionRouter.dispatch(resolved)
                    _actionResults.emit(result)
                } else {
                    _unhandledText.emit(event.text)
                }

                // Continue listening in continuous mode.
                if (_config.continuous) {
                    _state.value = AgentState.LISTENING
                    engine.startListening(_config.language)
                } else {
                    _state.value = AgentState.IDLE
                }
            }

            is SpeechEvent.PartialResult -> {
                if (_config.partialResults) {
                    _transcript.emit(event.text)
                }
            }

            is SpeechEvent.Error -> {
                _errors.emit(VoiceAgentError.EngineError(event.code, event.message))
                // Restart on recoverable errors in continuous mode.
                if (_config.continuous) {
                    delay(RESTART_DELAY_MS)
                    engine.startListening(_config.language)
                }
            }

            is SpeechEvent.RmsChanged -> {
                _audioLevel.value = event.level.coerceIn(0f, 1f)
            }

            is SpeechEvent.EndOfSpeech -> {
                // The engine stopped because of silence. Auto-restart is
                // handled when FinalResult arrives.
            }

            is SpeechEvent.ReadyForSpeech -> {
                _state.value = AgentState.LISTENING
            }
        }
    }

    private companion object {
        /** Delay before restarting after an error, in milliseconds. */
        const val RESTART_DELAY_MS = 500L
    }
}
