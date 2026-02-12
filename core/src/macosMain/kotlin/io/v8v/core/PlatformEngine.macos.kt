package io.v8v.core

/**
 * macOS implementation of [createPlatformEngine].
 *
 * Returns a [MacosSpeechEngine] backed by SFSpeechRecognizer + AVAudioEngine.
 */
actual fun createPlatformEngine(context: Any?): SpeechRecognitionEngine =
    MacosSpeechEngine()
