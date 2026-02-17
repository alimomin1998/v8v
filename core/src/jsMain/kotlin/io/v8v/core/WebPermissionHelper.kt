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
            val permissions = js("navigator.permissions")
            if (permissions != null && permissions != undefined) {
                val queryArg = js("({name: 'microphone'})")
                val result = (permissions.query(queryArg) as Promise<dynamic>).await()
                when (result.state as String) {
                    "granted" -> PermissionStatus.GRANTED
                    "denied" -> PermissionStatus.DENIED
                    else -> PermissionStatus.NOT_DETERMINED
                }
            } else {
                // Permissions API not available — try requesting directly
                PermissionStatus.NOT_DETERMINED
            }
        } catch (_: dynamic) {
            // Query failed — treat as undetermined so we attempt getUserMedia
            PermissionStatus.NOT_DETERMINED
        }

    override suspend fun requestMicrophonePermission(): PermissionStatus =
        try {
            val mediaDevices = js("navigator.mediaDevices")
            if (mediaDevices != null && mediaDevices != undefined) {
                val constraints = js("({audio: true})")
                val stream = (mediaDevices.getUserMedia(constraints) as Promise<dynamic>).await()
                // Got permission — stop the stream immediately (we just needed the permission)
                val tracks = stream.getTracks() as Array<dynamic>
                tracks.forEach { track -> track.stop() }
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.DENIED
            }
        } catch (e: dynamic) {
            console.log("Microphone permission request failed:", e)
            PermissionStatus.DENIED
        }

    private val console get() = js("console")
}
