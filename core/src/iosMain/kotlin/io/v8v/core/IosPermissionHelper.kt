package io.v8v.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioSession
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import kotlin.coroutines.resume

/**
 * iOS implementation of [PermissionHelper].
 *
 * Handles two permissions required for voice recognition:
 * 1. **Microphone** — via [AVAudioSession.requestRecordPermission]
 * 2. **Speech Recognition** — via [SFSpeechRecognizer.requestAuthorization]
 *
 * **Info.plist keys required:**
 * - `NSSpeechRecognitionUsageDescription`
 * - `NSMicrophoneUsageDescription`
 */
@OptIn(ExperimentalForeignApi::class)
class IosPermissionHelper : PermissionHelper {

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
