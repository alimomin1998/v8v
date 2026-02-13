package io.v8v.core

import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * Web/Browser implementation of [PermissionHelper].
 *
 * Checks microphone access via `navigator.permissions.query` and requests
 * access via `navigator.mediaDevices.getUserMedia`.
 */
class WebPermissionHelper : PermissionHelper {
    override suspend fun checkMicrophonePermission(): PermissionStatus =
        try {
            val navigator = js("navigator")
            val permissions = navigator.permissions
            if (permissions != null) {
                val result = (permissions.query(js("{name: 'microphone'}")) as Promise<dynamic>).await()
                when (result.state as String) {
                    "granted" -> PermissionStatus.GRANTED
                    "denied" -> PermissionStatus.DENIED
                    else -> PermissionStatus.NOT_DETERMINED
                }
            } else {
                PermissionStatus.NOT_DETERMINED
            }
        } catch (_: dynamic) {
            PermissionStatus.NOT_DETERMINED
        }

    override suspend fun requestMicrophonePermission(): PermissionStatus =
        try {
            val navigator = js("navigator")
            val mediaDevices = navigator.mediaDevices
            if (mediaDevices != null) {
                val stream =
                    (
                        mediaDevices.getUserMedia(
                            js("{audio: true}"),
                        ) as Promise<dynamic>
                    ).await()
                // Got permission — stop the stream immediately (we just needed the permission)
                val tracks = stream.getTracks() as Array<dynamic>
                tracks.forEach { track -> track.stop() }
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.DENIED
            }
        } catch (_: dynamic) {
            PermissionStatus.DENIED
        }
}
