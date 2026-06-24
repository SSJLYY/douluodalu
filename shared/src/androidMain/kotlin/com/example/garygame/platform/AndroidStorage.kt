package com.example.garygame.platform

import android.content.Context

class AndroidStorage(context: Context, name: String) : PlatformStorage {
    private val sp = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val editor = sp.edit()

    override fun putInt(key: String, value: Int) { editor.putInt(key, value) }
    override fun putLong(key: String, value: Long) { editor.putLong(key, value) }
    override fun putString(key: String, value: String?) { editor.putString(key, value) }
    override fun putBoolean(key: String, value: Boolean) { editor.putBoolean(key, value) }
    override fun getInt(key: String, default: Int): Int = sp.getInt(key, default)
    override fun getLong(key: String, default: Long): Long = sp.getLong(key, default)
    override fun getString(key: String, default: String?): String? = sp.getString(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = sp.getBoolean(key, default)
    override fun remove(key: String) { editor.remove(key) }
    override fun apply() { editor.apply() }
}
