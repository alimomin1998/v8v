package io.v8v.core

import io.v8v.core.model.VoiceAgentConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for [VoiceAgent] event handling, silence timeout promotion,
 * and intent dispatch.
 *
 * Uses a [FakeEngine] to inject speech events without a real microphone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceAgentTest {
    // ── Fake engine for testing ──

    class FakeEngine : SpeechRecognitionEngine {
        private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
        override val events: SharedFlow<SpeechEvent> = _events.asSharedFlow()

        private val _isListening = MutableStateFlow(false)
        override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

        var startCount = 0
            private set
        var stopCount = 0
            private set

        override fun startListening(language: String) {
            startCount++
            _isListening.value = true
            _events.tryEmit(SpeechEvent.ReadyForSpeech)
        }

        override fun stopListening() {
            stopCount++
            _isListening.value = false
        }

        override fun destroy() {
            stopListening()
        }

        /** Inject a speech event for testing. */
        fun emit(event: SpeechEvent) {
            _events.tryEmit(event)
        }
    }

    // ── Tests ──

    @Test
    fun final_result_triggers_intent_resolution() =
        runTest {
            val engine = FakeEngine()
            val config =
                VoiceAgentConfig(
                    language = "en",
                    continuous = false,
                    silenceTimeoutMs = 0, // disable silence promotion for this test
                )
            val agent = VoiceAgent(engine = engine, config = config, coroutineContext = coroutineContext)

            var handlerCalled = false
            var extractedText = ""
            agent.registerAction(
                intent = "todo.add",
                phrases = mapOf("en" to listOf("add *")),
            ) { resolved ->
                handlerCalled = true
                extractedText = resolved.extractedText
            }

            // Collect action results in background
            val results = mutableListOf<ActionResult>()
            val collectJob =
                launch {
                    agent.actionResults.collect { results.add(it) }
                }

            agent.start()
            advanceUntilIdle() // ensure collection coroutine is active

            // Simulate engine producing a final result
            engine.emit(SpeechEvent.FinalResult("add milk"))

            // Allow coroutines to process
            advanceTimeBy(100)

            assertEquals(true, handlerCalled)
            assertEquals("milk", extractedText)
            assertEquals(1, results.size)
            assertIs<ActionResult.Success>(results[0])

            collectJob.cancel()
            agent.destroy()
        }

    @Test
    fun unmatched_text_emitted_to_unhandled_flow() =
        runTest {
            val engine = FakeEngine()
            val config =
                VoiceAgentConfig(
                    language = "en",
                    continuous = false,
                    silenceTimeoutMs = 0,
                )
            val agent = VoiceAgent(engine = engine, config = config, coroutineContext = coroutineContext)

            agent.registerAction(
                intent = "todo.add",
                phrases = mapOf("en" to listOf("add *")),
            ) { }

            val unhandled = mutableListOf<String>()
            val collectJob =
                launch {
                    agent.unhandledText.collect { unhandled.add(it) }
                }

            agent.start()
            advanceUntilIdle() // ensure collection coroutine is active
            engine.emit(SpeechEvent.FinalResult("hello world"))
            advanceTimeBy(100)

            assertEquals(1, unhandled.size)
            assertEquals("hello world", unhandled[0])

            collectJob.cancel()
            agent.destroy()
        }

    @Test
    fun silence_timeout_promotes_partial_to_final() =
        runTest {
            val engine = FakeEngine()
            val config =
                VoiceAgentConfig(
                    language = "en",
                    continuous = false,
                    partialResults = true,
                    silenceTimeoutMs = 1000, // 1 second timeout
                )
            val agent = VoiceAgent(engine = engine, config = config, coroutineContext = coroutineContext)

            var handlerCalled = false
            agent.registerAction(
                intent = "todo.add",
                phrases = mapOf("en" to listOf("add *")),
            ) { handlerCalled = true }

            agent.start()
            advanceUntilIdle() // ensure collection coroutine is active

            // Emit partial result (no isFinal)
            engine.emit(SpeechEvent.PartialResult("add milk"))
            advanceTimeBy(500) // Not enough time yet
            assertEquals(false, handlerCalled)

            // Wait for silence timeout to promote
            advanceTimeBy(600) // Total 1100ms > 1000ms timeout
            assertEquals(true, handlerCalled)

            agent.destroy()
        }

    @Test
    fun silence_timeout_resets_on_new_partial() =
        runTest {
            val engine = FakeEngine()
            val config =
                VoiceAgentConfig(
                    language = "en",
                    continuous = false,
                    partialResults = true,
                    silenceTimeoutMs = 1000,
                )
            val agent = VoiceAgent(engine = engine, config = config, coroutineContext = coroutineContext)

            var extractedText = ""
            agent.registerAction(
                intent = "todo.add",
                phrases = mapOf("en" to listOf("add *")),
            ) { resolved -> extractedText = resolved.extractedText }

            agent.start()
            advanceUntilIdle() // ensure collection coroutine is active

            // First partial
            engine.emit(SpeechEvent.PartialResult("add"))
            advanceTimeBy(800) // 800ms, not enough

            // New partial resets timer
            engine.emit(SpeechEvent.PartialResult("add milk"))
            advanceTimeBy(800) // Only 800ms since new partial
            assertEquals("", extractedText) // Still not fired

            advanceTimeBy(300) // Now 1100ms since last partial
            assertEquals("milk", extractedText) // Now it fired with latest text

            agent.destroy()
        }

    @Test
    fun continuous_mode_restarts_after_final() =
        runTest {
            val engine = FakeEngine()
            val config =
                VoiceAgentConfig(
                    language = "en",
                    continuous = true,
                    silenceTimeoutMs = 0,
                )
            val agent = VoiceAgent(engine = engine, config = config, coroutineContext = coroutineContext)

            agent.registerAction(
                intent = "todo.add",
                phrases = mapOf("en" to listOf("add *")),
            ) { }

            agent.start()
            advanceUntilIdle() // ensure collection coroutine is active
            val startsBefore = engine.startCount

            engine.emit(SpeechEvent.FinalResult("add milk"))
            advanceTimeBy(100)

            // Engine should have been restarted for continuous mode
            assertEquals(startsBefore + 1, engine.startCount)

            agent.destroy()
        }

    @Test
    fun update_config_changes_language() =
        runTest {
            val engine = FakeEngine()
            val agent =
                VoiceAgent(
                    engine = engine,
                    config = VoiceAgentConfig(language = "en"),
                    coroutineContext = coroutineContext,
                )

            agent.start()
            advanceUntilIdle() // ensure collection coroutine is active
            val startsBefore = engine.startCount

            // Change language while listening
            agent.updateConfig(VoiceAgentConfig(language = "es"))
            advanceTimeBy(100)

            // Should have restarted with new language
            assertEquals(startsBefore + 1, engine.startCount)

            agent.destroy()
        }

    @Test
    fun stop_cancels_silence_promotion() =
        runTest {
            val engine = FakeEngine()
            val config =
                VoiceAgentConfig(
                    language = "en",
                    continuous = false,
                    partialResults = true,
                    silenceTimeoutMs = 1000,
                )
            val agent = VoiceAgent(engine = engine, config = config, coroutineContext = coroutineContext)

            var handlerCalled = false
            agent.registerAction(
                intent = "todo.add",
                phrases = mapOf("en" to listOf("add *")),
            ) { handlerCalled = true }

            agent.start()
            advanceUntilIdle() // ensure collection coroutine is active
            engine.emit(SpeechEvent.PartialResult("add milk"))
            advanceTimeBy(500)

            // Stop before silence timeout fires
            agent.stop()
            advanceTimeBy(1000)

            // Handler should NOT have been called
            assertEquals(false, handlerCalled)

            agent.destroy()
        }

    @Test
    fun benign_errors_are_filtered() =
        runTest {
            val engine = FakeEngine()
            val config =
                VoiceAgentConfig(
                    language = "en",
                    continuous = true,
                    silenceTimeoutMs = 0,
                )
            val agent = VoiceAgent(engine = engine, config = config, coroutineContext = coroutineContext)

            val errors = mutableListOf<VoiceAgentError>()
            val collectJob =
                launch {
                    agent.errors.collect { errors.add(it) }
                }

            agent.start()
            advanceUntilIdle() // ensure collection coroutine is active

            // Code 1110 = "No speech detected" — benign, should be filtered
            engine.emit(SpeechEvent.Error(code = 1110, message = "No speech detected"))
            advanceTimeBy(100)
            assertEquals(0, errors.size) // Should not be emitted

            // Code -999 = real error — should be emitted
            engine.emit(SpeechEvent.Error(code = -999, message = "Real error"))
            advanceTimeBy(100)
            assertEquals(1, errors.size)

            collectJob.cancel()
            agent.destroy()
        }
}
