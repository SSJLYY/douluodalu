package com.example.garygame.platform

import kotlinx.browser.localStorage

class WebStorage(private val prefix: String = "") : PlatformStorage {
    private fun fullKey(key: String) = if (prefix.isEmpty()) key else "$prefix:$key"

    override fun putInt(key: String, value: Int) { localStorage.setItem(fullKey(key), value.toString()) }
    override fun putLong(key: String, value: Long) { localStorage.setItem(fullKey(key), value.toString()) }
    override fun putString(key: String, value: String?) {
        if (value == null) localStorage.removeItem(fullKey(key))
        else localStorage.setItem(fullKey(key), value)
    }
    override fun putBoolean(key: String, value: Boolean) { localStorage.setItem(fullKey(key), if (value) "1" else "0") }
    override fun getInt(key: String, default: Int): Int = localStorage.getItem(fullKey(key))?.toIntOrNull() ?: default
    override fun getLong(key: String, default: Long): Long = localStorage.getItem(fullKey(key))?.toLongOrNull() ?: default
    override fun getString(key: String, default: String?): String? = localStorage.getItem(fullKey(key)) ?: default
    override fun getBoolean(key: String, default: Boolean): Boolean {
        val v = localStorage.getItem(fullKey(key)) ?: return default
        return v == "1" || v.equals("true", ignoreCase = true)
    }
    override fun remove(key: String) { localStorage.removeItem(fullKey(key)) }
    override fun apply() { /* localStorage is synchronous, no-op */ }
}
