package com.example.garygame.platform

import kotlinx.serialization.json.*

class GameJsonObject {
    val map = mutableMapOf<String, JsonElement>()

    fun put(key: String, value: Int) { map[key] = JsonPrimitive(value) }
    fun put(key: String, value: Long) { map[key] = JsonPrimitive(value) }
    fun put(key: String, value: String?) {
        if (value == null) map[key] = JsonNull
        else map[key] = JsonPrimitive(value)
    }
    fun put(key: String, value: Boolean) { map[key] = JsonPrimitive(value) }
    fun put(key: String, value: GameJsonArray) { map[key] = value.toJsonArray() }
    fun putRaw(key: String, element: JsonElement) { map[key] = element }

    fun getInt(key: String, default: Int = 0): Int =
        (map[key] as? JsonPrimitive)?.intOrNull ?: default
    fun getLong(key: String, default: Long = 0L): Long =
        (map[key] as? JsonPrimitive)?.longOrNull ?: default
    fun getString(key: String, default: String = ""): String =
        (map[key] as? JsonPrimitive)?.contentOrNull ?: default
    fun optString(key: String, default: String = ""): String = getString(key, default)
    fun optInt(key: String, default: Int = 0): Int = getInt(key, default)
    fun optLong(key: String, default: Long = 0L): Long = getLong(key, default)
    fun has(key: String): Boolean = map.containsKey(key) && map[key] !is JsonNull

    fun optJSONArray(key: String): GameJsonArray? {
        val element = map[key] ?: return null
        if (element is JsonArray) return GameJsonArray(element)
        return null
    }

    fun keys(): Set<String> = map.keys

    fun toJsonElement(): JsonObject = JsonObject(map)

    fun toJsonString(): String {
        val json = Json { isLenient = true; ignoreUnknownKeys = true }
        return json.encodeToString(JsonObject.serializer(), toJsonElement())
    }

    companion object {
        private val json = Json { isLenient = true; ignoreUnknownKeys = true }

        fun fromString(str: String): GameJsonObject {
            val result = GameJsonObject()
            try {
                val obj = json.parseToJsonElement(str).jsonObject
                for ((k, v) in obj) {
                    result.map[k] = v
                }
            } catch (_: Exception) {}
            return result
        }
    }
}

class GameJsonArray {
    val list = mutableListOf<JsonElement>()

    constructor()
    constructor(arr: JsonArray) {
        list.addAll(arr)
    }

    fun put(value: Int) { list.add(JsonPrimitive(value)) }
    fun put(value: Long) { list.add(JsonPrimitive(value)) }
    fun put(value: String) { list.add(JsonPrimitive(value)) }
    fun put(value: GameJsonObject) { list.add(value.toJsonElement()) }

    fun length(): Int = list.size

    fun getInt(index: Int): Int =
        (list.getOrNull(index) as? JsonPrimitive)?.intOrNull ?: 0
    fun getString(index: Int): String =
        (list.getOrNull(index) as? JsonPrimitive)?.contentOrNull ?: ""

    fun getJSONObject(index: Int): GameJsonObject {
        val element = list.getOrNull(index)
        if (element is JsonObject) {
            val result = GameJsonObject()
            for ((k, v) in element) {
                result.map[k] = v
            }
            return result
        }
        return GameJsonObject()
    }

    fun toJsonArray(): JsonArray = JsonArray(list)

    fun toJsonString(): String {
        val json = Json { isLenient = true; ignoreUnknownKeys = true }
        return json.encodeToString(JsonArray.serializer(), toJsonArray())
    }

    companion object {
        private val json = Json { isLenient = true; ignoreUnknownKeys = true }

        fun fromString(str: String): GameJsonArray {
            val result = GameJsonArray()
            try {
                val arr = json.parseToJsonElement(str).jsonArray
                for (element in arr) {
                    result.list.add(element)
                }
            } catch (_: Exception) {}
            return result
        }
    }
}
