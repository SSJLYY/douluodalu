package com.example.garygame.platform

actual object PlatformTime {
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
}
