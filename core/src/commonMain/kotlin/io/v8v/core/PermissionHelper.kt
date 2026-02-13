package io.v8v.core

/**
 * Status of a platform permission.
 */
enum class PermissionStatus {
    /** Permission has been granted by the user. */
    GRANTED,

    /** Permission has been explicitly denied by the user. */
    DENIED,

    /** Permission has not been requested yet. */
    NOT_DETERMINED,
}

/**
 * Platform-agnostic abstraction for microphone and speech recognition permissions.
 *
 * Each platform provides its own implementation:
 * - **Android** → `ContextCompat.checkSelfPermission` / `ActivityResultContracts`
 * - **iOS** → `AVAudioSession.requestRecordPermission` / `SFSpeechRecognizer.requestAuthorization`
 * - **Web** → `navigator.mediaDevices.getUserMedia`
 * - **JVM** → Always returns [PermissionStatus.GRANTED] (desktop has no permission model)
 *
 * Usage:
 * ```kotlin
 * val helper: PermissionHelper = ...
 * val status = helper.checkMicrophonePermission()
 * if (status != PermissionStatus.GRANTED) {
 *     helper.requestMicrophonePermission()
 * }
 * ```
 */
interface PermissionHelper {
    /**
     * Check the current microphone permission status without prompting the user.
     */
    suspend fun checkMicrophonePermission(): PermissionStatus

    /**
     * Request microphone permission from the user.
     *
     * On platforms that show a system dialog (Android, iOS, Web), this
     * suspends until the user responds. On platforms with no permission
     * model (JVM), this returns [PermissionStatus.GRANTED] immediately.
     *
     * @return The resulting permission status after the request.
     */
    suspend fun requestMicrophonePermission(): PermissionStatus
}
