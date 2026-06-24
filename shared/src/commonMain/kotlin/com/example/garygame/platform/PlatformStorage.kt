package com.example.garygame.platform

interface PlatformStorage {
    fun putInt(key: String, value: Int)
    fun putLong(key: String, value: Long)
    fun putString(key: String, value: String?)
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun getString(key: String, default: String?): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun remove(key: String)
    fun apply()
}
