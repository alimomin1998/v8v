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
import kotlin.coroutines.CoroutineContext

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
 * @param coroutineContext The [CoroutineContext] used for the agent's internal
 *   coroutine scope. Defaults to [Dispatchers.Default]. On Android you may
 *   pass [Dispatchers.Main]; in tests pass the test dispatcher.
 */
class VoiceAgent(
    private val engine: SpeechRecognitionEngine,
    config: VoiceAgentConfig = VoiceAgentConfig(),
    private val permissionHelper: PermissionHelper? = null,
    coroutineContext: CoroutineContext = Dispatchers.Default,
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
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())

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
     * Job for the silence-timeout promotion timer. When a partial result
     * arrives and no further speech follows within [VoiceAgentConfig.silenceTimeoutMs],
     * the partial is automatically promoted to a final result and processed
     * through intent resolution. This handles engines (like macOS SFSpeechRecognizer)
     * that don't reliably set `isFinal`.
     */
    private var silencePromotionJob: Job? = null
    private var lastPartialText: String? = null

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

        listeningJob =
            scope.launch {
                // Check (and request if needed) permission when a helper is available.
                if (permissionHelper != null) {
                    var status = permissionHelper.checkMicrophonePermission()
                    if (status == PermissionStatus.NOT_DETERMINED) {
                        status = permissionHelper.requestMicrophonePermission()
                    }
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
        silencePromotionJob?.cancel()
        silencePromotionJob = null
        lastPartialText = null
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
                // Cancel any pending silence promotion — we got a real final.
                silencePromotionJob?.cancel()
                silencePromotionJob = null
                lastPartialText = null

                processFinalResult(event.text)
            }

            is SpeechEvent.PartialResult -> {
                if (_config.partialResults) {
                    _transcript.emit(event.text)
                }

                // ── Silence timeout: auto-promote partial → final ──
                // If the engine doesn't send isFinal (common on macOS),
                // we promote the last partial after a configurable timeout.
                val timeoutMs = _config.silenceTimeoutMs
                if (timeoutMs > 0) {
                    lastPartialText = event.text
                    silencePromotionJob?.cancel()
                    silencePromotionJob =
                        scope.launch {
                            delay(timeoutMs)
                            val text = lastPartialText ?: return@launch
                            lastPartialText = null
                            // Do NOT call engine.stopListening() here — processFinalResult
                            // will call engine.startListening() (which stops first) in
                            // continuous mode, or stop() will be called in non-continuous.
                            processFinalResult(text)
                        }
                }
            }

            is SpeechEvent.Error -> {
                // ── Filter benign errors ──
                // Apple's SFSpeechRecognizer fires these when:
                //   1110 = "No speech detected" (timeout, user not speaking)
                //   216  = "Recognition request was cancelled" (our stopListening)
                //   301  = "Recognition retry" (transient)
                //   203  = "Retry" (transient)
                // These are NOT real errors — they're expected during normal
                // continuous-mode lifecycle. Don't show them or restart.
                val isBenign = event.code in BENIGN_ERROR_CODES

                if (!isBenign) {
                    _errors.emit(VoiceAgentError.EngineError(event.code, event.message))
                }

                // Only restart on genuine errors in continuous mode.
                // For benign errors (no speech, cancelled), just keep
                // the current state — the silence promotion handles restarts.
                // DO NOT restart here for benign errors to avoid the
                // start→no speech→error→restart→start→no speech... loop.
                if (!isBenign && _config.continuous && _state.value != AgentState.IDLE) {
                    delay(RESTART_DELAY_MS)
                    engine.startListening(_config.language)
                }
            }

            is SpeechEvent.RmsChanged -> {
                _audioLevel.value = event.level.coerceIn(0f, 1f)
            }

            is SpeechEvent.EndOfSpeech -> {
                // The engine stopped because of silence. Auto-restart is
                // handled when FinalResult arrives or by silence promotion.
            }

            is SpeechEvent.ReadyForSpeech -> {
                _state.value = AgentState.LISTENING
            }
        }
    }

    /**
     * Process a finalized transcript: resolve intent, dispatch action,
     * and optionally restart in continuous mode.
     */
    private suspend fun processFinalResult(text: String) {
        _state.value = AgentState.PROCESSING
        _transcript.emit(text)

        val resolved = intentResolver.resolve(text, _config.language, _config.fuzzyThreshold)
        if (resolved != null) {
            val result = actionRouter.dispatch(resolved)
            _actionResults.emit(result)
        } else {
            _unhandledText.emit(text)
        }

        // Continue listening in continuous mode.
        // engine.startListening() internally calls stopListening() first,
        // so the old recognition task is properly cleaned up.
        if (_config.continuous) {
            _state.value = AgentState.LISTENING
            engine.startListening(_config.language)
        } else {
            _state.value = AgentState.IDLE
        }
    }

    private companion object {
        /** Delay before restarting after a real (non-benign) error. */
        const val RESTART_DELAY_MS = 1000L

        /**
         * Error codes from Apple's SFSpeechRecognizer that are expected
         * during normal operation and should not trigger error events or
         * cause continuous-mode restart loops.
         */
        val BENIGN_ERROR_CODES =
            setOf(
                1110, // kAFAssistantErrorDomain: No speech detected
                216, // kAFAssistantErrorDomain: Request was cancelled
                301, // kAFAssistantErrorDomain: Recognition retry
                203, // kAFAssistantErrorDomain: Retry
                102, // kAFAssistantErrorDomain: Assets not installed
                201, // kAFAssistantErrorDomain: Request was superseded
                209, // kAFAssistantErrorDomain: Connection invalidated
            )
    }
}
