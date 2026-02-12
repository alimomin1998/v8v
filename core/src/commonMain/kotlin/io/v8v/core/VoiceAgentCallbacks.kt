package io.v8v.core

import io.v8v.core.model.ResolvedIntent
import io.v8v.core.model.VoiceAgentConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Callback-based facade for [VoiceAgent], designed for platforms where
 * Kotlin Flows cannot be directly observed (iOS, macOS via Swift/ObjC).
 *
 * Instead of collecting Flows, consumers register simple callbacks that
 * are invoked on the main thread when events occur.
 *
 * Usage from Swift:
 * ```swift
 * let agent = VoiceAgentCallbacks(engine: MacosSpeechEngine(), config: config)
 * agent.onTranscript { text in print("Heard: \(text)") }
 * agent.onError { msg in print("Error: \(msg)") }
 * agent.registerAction(intent: "todo.add", phrases: ["en-US": ["add *"]]) { resolved in
 *     print("Add: \(resolved.extractedText)")
 * }
 * agent.start()
 * ```
 */
class VoiceAgentCallbacks(
    engine: SpeechRecognitionEngine,
    config: VoiceAgentConfig = VoiceAgentConfig(),
    permissionHelper: PermissionHelper? = null,
) {
    private val agent = VoiceAgent(engine, config, permissionHelper)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Callback registration ──

    /** Called with each final (and optionally partial) transcript. */
    fun onTranscript(callback: (String) -> Unit) {
        scope.launch {
            agent.transcript.collect { callback(it) }
        }
    }

    /** Called when an error occurs. */
    fun onError(callback: (String) -> Unit) {
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

    /** Called with every action result (success or error). */
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

    /** Register a local voice action with a callback handler. */
    fun registerAction(
        intent: String,
        phrases: Map<String, List<String>>,
        handler: (ResolvedIntent) -> Unit,
    ) {
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
        scope.cancel()
    }
}
