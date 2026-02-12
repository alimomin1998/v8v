package io.v8v.core

/**
 * Creates the default platform-specific [SpeechRecognitionEngine].
 *
 * - **Android**: Pass the application [android.content.Context].
 * - **JVM**: Not supported — throws [UnsupportedOperationException].
 *   Provide your own [SpeechRecognitionEngine] implementation instead.
 * - **iOS / Web**: Future platform adapters.
 *
 * @param context Platform-specific context required to initialize the engine.
 */
expect fun createPlatformEngine(context: Any? = null): SpeechRecognitionEngine
