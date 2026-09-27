package com.nothing.one.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Tags live in the entity as a JSON array string; this is the only gateway. */
object Tags {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())

    fun encode(tags: List<String>): String = json.encodeToString(serializer, tags)

    fun decode(raw: String): List<String> =
        try {
            json.decodeFromString(serializer, raw)
        } catch (_: Exception) {
            emptyList()
        }
}
