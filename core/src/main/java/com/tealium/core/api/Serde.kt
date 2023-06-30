package com.tealium.core.api

import org.json.JSONArray
import org.json.JSONObject


interface Serde<T, R> {
    val serializer: Serializer<T, R>
    val deserializer: Deserializer<R, T>
}

interface Serializer<T, R> {
    fun serialize(value: T): R
}

interface Deserializer<T, R> {
    fun deserialize(value: T): R
}