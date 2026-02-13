package io.v8v.core

/**
 * iOS implementation of [createPlatformEngine].
 *
 * Returns an [IosSpeechEngine] backed by Apple's SFSpeechRecognizer + AVAudioSession.
 */
actual fun createPlatformEngine(context: Any?): SpeechRecognitionEngine = IosSpeechEngine()
