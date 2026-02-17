package io.v8v.remote

import kotlin.js.Date

/**
 * JS/Browser implementation of [currentTimestamp].
 *
 * Uses JavaScript's `Date.toISOString()` for ISO-8601 format.
 */
internal actual fun currentTimestamp(): String = Date().toISOString()
