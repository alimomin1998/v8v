package io.v8v.core

/**
 * JS/Browser implementation of [createPlatformEngine].
 *
 * Returns a [WebSpeechEngine] backed by the Web Speech API.
 * The `context` parameter is ignored on this platform.
 */
actual fun createPlatformEngine(context: Any?): SpeechRecognitionEngine = WebSpeechEngine()
