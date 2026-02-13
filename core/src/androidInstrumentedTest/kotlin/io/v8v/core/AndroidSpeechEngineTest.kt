package io.v8v.core

import android.speech.SpeechRecognizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    @After
    fun tearDown() {
        engine?.destroy()
    }

    @Test
    fun engine_can_be_created() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        engine = AndroidSpeechEngine(context)
        assertNotNull(engine)
    }

    @Test
    fun engine_reports_not_listening_initially() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        engine = AndroidSpeechEngine(context)
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

        engine = AndroidSpeechEngine(context)

        engine!!.startListening("en")

        // Give it a moment to initialize, then stop
        Thread.sleep(500)
        engine!!.stopListening()
        assertFalse(engine!!.isListening.value)
    }

    @Test
    fun engine_destroy_is_idempotent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        engine = AndroidSpeechEngine(context)

        engine!!.destroy()
        engine!!.destroy() // Second call should not crash
        engine = null // Prevent double-destroy in tearDown
    }
}
