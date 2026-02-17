package io.v8v.remote

import java.time.Instant

internal actual fun currentTimestamp(): String = Instant.now().toString()
