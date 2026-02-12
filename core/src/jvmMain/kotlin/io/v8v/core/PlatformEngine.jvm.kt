package io.v8v.core

/**
 * JVM does not have a built-in speech recognition engine.
 *
 * Developers targeting JVM should provide their own
 * [SpeechRecognitionEngine] implementation and pass it
 * directly to [VoiceAgent].
 */
actual fun createPlatformEngine(context: Any?): SpeechRecognitionEngine {
    throw UnsupportedOperationException(
        "JVM does not have a built-in speech recognition engine. " +
            "Provide a custom SpeechRecognitionEngine implementation to VoiceAgent."
    )
}
