package io.v8v.core

/**
 * JVM implementation of [PermissionHelper].
 *
 * Desktop JVM has no microphone permission model, so this always
 * returns [PermissionStatus.GRANTED].
 */
class JvmPermissionHelper : PermissionHelper {
    override suspend fun checkMicrophonePermission(): PermissionStatus = PermissionStatus.GRANTED

    override suspend fun requestMicrophonePermission(): PermissionStatus = PermissionStatus.GRANTED
}
