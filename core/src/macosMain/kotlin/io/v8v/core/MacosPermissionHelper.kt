package io.v8v.core

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import kotlin.coroutines.resume

/**
 * macOS implementation of [PermissionHelper].
 *
 * On macOS, microphone access is controlled by the system (via entitlements
 * and the `com.apple.security.device.audio-input` entitlement). This helper
 * requests speech recognition authorization via [SFSpeechRecognizer].
 *
 * **Entitlements required:**
 * - `com.apple.security.device.audio-input`
 *
 * **Info.plist keys required:**
 * - `NSSpeechRecognitionUsageDescription`
 */
class MacosPermissionHelper : PermissionHelper {

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
        // On macOS, microphone permission is handled at the entitlement/system level.
        // We only need to request speech recognition authorization.
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
