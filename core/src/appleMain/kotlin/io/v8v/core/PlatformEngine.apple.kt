package io.v8v.core

/**
 * Apple platform (iOS + macOS) implementation of [createPlatformEngine].
 *
 * Returns an [AppleSpeechEngine] backed by Apple's SFSpeechRecognizer.
 * The `context` parameter is ignored on this platform.
 */
actual fun createPlatformEngine(context: Any?): SpeechRecognitionEngine =
    AppleSpeechEngine()
