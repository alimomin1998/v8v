package io.v8v.core

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioSession
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import kotlin.coroutines.resume

/**
 * Apple platform implementation of [PermissionHelper].
 *
 * Works on both **iOS** and **macOS**. Handles two permissions required
 * for voice recognition:
 * 1. **Microphone** -- via [AVAudioSession.requestRecordPermission]
 * 2. **Speech Recognition** -- via [SFSpeechRecognizer.requestAuthorization]
 *
 * Both must be granted for speech recognition to work. The helper requests
 * both and returns [PermissionStatus.GRANTED] only if both are granted.
 *
 * **Info.plist keys required (iOS):**
 * - `NSSpeechRecognitionUsageDescription`
 * - `NSMicrophoneUsageDescription`
 *
 * **Entitlements required (macOS):**
 * - `com.apple.security.device.audio-input`
 */
class ApplePermissionHelper : PermissionHelper {

    override suspend fun checkMicrophonePermission(): PermissionStatus {
        return when (SFSpeechRecognizer.authorizationStatus()) {
            SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized ->
                PermissionStatus.GRANTED
            SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusDenied,
            SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusRestricted ->
                PermissionStatus.DENIED
            else -> PermissionStatus.NOT_DETERMINED
        }
    }

    override suspend fun requestMicrophonePermission(): PermissionStatus {
        // Request microphone permission first
        val micGranted = suspendCancellableCoroutine { continuation ->
            AVAudioSession.sharedInstance().requestRecordPermission { granted ->
                if (continuation.isActive) {
                    continuation.resume(granted)
                }
            }
        }

        if (!micGranted) return PermissionStatus.DENIED

        // Then request speech recognition permission
        val speechGranted = suspendCancellableCoroutine { continuation ->
            SFSpeechRecognizer.requestAuthorization { status ->
                if (continuation.isActive) {
                    continuation.resume(
                        status == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized,
                    )
                }
            }
        }

        return if (speechGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }
}
