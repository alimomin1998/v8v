package io.v8v.core

import android.speech.SpeechRecognizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android instrumented tests for [AndroidSpeechEngine].
 *
 * These tests run on a real device or emulator. They validate that the engine
 * can be created, started, and stopped without crashing — actual speech input
 * is not tested (requires a mic).
 *
 * Tests that call [AndroidSpeechEngine.startListening] are skipped on
 * emulators without speech recognition services (typical for CI).
 *
 * Run with:
 *   ./gradlew :core:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AndroidSpeechEngineTest {

    private var engine: AndroidSpeechEngine? = null
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun <T> runOnMainSync(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync {
            result.set(block())
        }
        return result.get()
    }

    @After
    fun tearDown() {
        runOnMainSync {
            engine?.destroy()
            engine = null
        }
    }

    @Test
    fun engine_can_be_created() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        engine = runOnMainSync { AndroidSpeechEngine(context) }
        assertNotNull(engine)
    }

    @Test
    fun engine_reports_not_listening_initially() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        engine = runOnMainSync { AndroidSpeechEngine(context) }
        assertFalse(engine!!.isListening.value)
    }

    @Test
    fun engine_start_and_stop_cycle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Skip on emulators without speech recognition (e.g. CI runners)
        Assume.assumeTrue(
            "SpeechRecognizer not available — skipping",
            SpeechRecognizer.isRecognitionAvailable(context),
        )

        engine = runOnMainSync { AndroidSpeechEngine(context) }

        runOnMainSync { engine!!.startListening("en") }

        // Give it a moment to initialize, then stop
        Thread.sleep(500)
        runOnMainSync { engine!!.stopListening() }
        assertFalse(engine!!.isListening.value)
    }

    @Test
    fun engine_destroy_is_idempotent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        engine = runOnMainSync { AndroidSpeechEngine(context) }

        runOnMainSync { engine!!.destroy() }
        runOnMainSync { engine!!.destroy() } // Second call should not crash
        engine = null // Prevent double-destroy in tearDown
    }
}
