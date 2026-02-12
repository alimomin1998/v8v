@file:OptIn(ExperimentalJsExport::class)

package io.v8v.core

import io.v8v.core.model.VoiceAgentConfig
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
 * import { VoiceAgentJs } from 'voice-agent-core';
 *
 * const agent = new VoiceAgentJs('en');
 * agent.registerPhrase('todo.add', 'en', 'add *');
 * agent.onTranscript(text => console.log('Heard:', text));
 * agent.onIntent((intent, text) => console.log(intent, text));
 * agent.onError(msg => console.error(msg));
 * agent.start();
 * ```
 */
@JsExport
class VoiceAgentJs(language: String = "en") {

    private val scope = MainScope()
    private val engine = WebSpeechEngine()
    private val permissionHelper = WebPermissionHelper()
    private val agent = VoiceAgent(
        engine = engine,
        config = VoiceAgentConfig(language = language),
        permissionHelper = permissionHelper,
    )

    /**
     * Register a voice command phrase pattern for an intent.
     *
     * @param intent Unique intent name (e.g. "todo.add").
     * @param language BCP-47 language tag (e.g. "en", "es").
     * @param phrase Pattern with `*` wildcards (e.g. "add * to list").
     */
    fun registerPhrase(intent: String, language: String, phrase: String) {
        agent.registerAction(intent, mapOf(language to listOf(phrase))) { /* no-op local handler */ }
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
     * Register a callback that fires when an intent is matched.
     *
     * @param callback Called with `(intentName, extractedText)`.
     */
    fun onIntent(callback: (String, String) -> Unit) {
        scope.launch {
            agent.actionResults.collect { result ->
                when (result) {
                    is ActionResult.Success -> callback(result.intent, result.message)
                    is ActionResult.Error -> { /* handled via onError */ }
                }
            }
        }
    }

    /**
     * Register a callback that fires when an error occurs.
     *
     * @param callback Called with the error message string.
     */
    fun onError(callback: (String) -> Unit) {
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
        scope.cancel()
    }
}
