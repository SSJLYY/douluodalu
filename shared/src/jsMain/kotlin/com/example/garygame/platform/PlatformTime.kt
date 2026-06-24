package com.example.garygame.platform

import kotlin.js.Date

actual object PlatformTime {
    actual fun currentTimeMillis(): Long = Date.now().toLong()
}
