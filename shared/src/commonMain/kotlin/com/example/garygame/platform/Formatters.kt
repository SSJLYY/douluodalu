package com.example.garygame.platform

fun formatDouble(value: Double, decimals: Int): String {
    val factor = listOf(1, 10, 100, 1000, 10000)[decimals.coerceIn(0, 4)]
    val rounded = (value * factor).toLong().toDouble() / factor
    val str = rounded.toString()
    val dotIndex = str.indexOf('.')
    return if (dotIndex < 0) {
        if (decimals > 0) str + "." + "0".repeat(decimals) else str
    } else {
        val intPart = str.substring(0, dotIndex)
        val decPart = str.substring(dotIndex + 1)
        intPart + "." + decPart.padEnd(decimals, '0').take(decimals)
    }
}
