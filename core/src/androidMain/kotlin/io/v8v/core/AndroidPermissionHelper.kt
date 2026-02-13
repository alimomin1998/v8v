package io.v8v.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android implementation of [PermissionHelper].
 *
 * Checks the `RECORD_AUDIO` permission using [ContextCompat].
 *
 * **Important:** [requestMicrophonePermission] requires the caller to supply
 * a [requestCallback] lambda that triggers the actual system permission dialog
 * (e.g. via `ActivityResultContracts.RequestPermission`). This is because
 * Android's permission API is tied to the Activity lifecycle and cannot be
 * called from a plain Context.
 *
 * Simple usage:
 * ```kotlin
 * val helper = AndroidPermissionHelper(context) { onResult ->
 *     permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
 *     // When the launcher callback fires, call onResult(granted)
 * }
 * ```
 *
 * @param context Application or Activity context for checking permissions.
 * @param requestCallback Called when [requestMicrophonePermission] is invoked.
 *   The callback receives a `(Boolean) -> Unit` that must be called with the
 *   grant result from the system dialog. If `null`, [requestMicrophonePermission]
 *   falls back to a check-only call and returns the current status.
 */
class AndroidPermissionHelper(
    private val context: Context,
    private val requestCallback: ((onResult: (Boolean) -> Unit) -> Unit)? = null,
) : PermissionHelper {
    override suspend fun checkMicrophonePermission(): PermissionStatus {
        val result = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        return if (result == PackageManager.PERMISSION_GRANTED) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.DENIED
        }
    }

    override suspend fun requestMicrophonePermission(): PermissionStatus {
        // If no request callback is provided, just check the current state.
        val callback = requestCallback ?: return checkMicrophonePermission()

        return suspendCancellableCoroutine { continuation ->
            callback { granted ->
                if (continuation.isActive) {
                    continuation.resume(
                        if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                    )
                }
            }
        }
    }
}
