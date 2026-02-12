package io.v8v.remote

import platform.Foundation.NSDate
import platform.Foundation.NSISO8601DateFormatter

/**
 * Apple platform (iOS + macOS) implementation of [currentTimestamp].
 *
 * Uses `NSISO8601DateFormatter` for ISO-8601 format.
 */
internal actual fun currentTimestamp(): String {
    val formatter = NSISO8601DateFormatter()
    return formatter.stringFromDate(NSDate())
}
