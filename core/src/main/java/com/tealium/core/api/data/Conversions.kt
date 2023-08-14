package com.tealium.core.api.data

import org.json.JSONArray
import org.json.JSONObject


/**
 * Utility function to allow transforming a [JSONObject]'s values.
 */
fun <T> JSONObject.mapValues(block: (value: Any) -> T): Map<String, T> {
    val map = mutableMapOf<String, T>()
    for (key in keys()) {
        opt(key)?.let { value ->
            map[key] = block(value)
        }
    }
    return map
}

/**
 * Utility function to allow [map] iterating on a [JSONArray]
 */
fun <T> JSONArray.map(transform: (value: Any) -> T) : List<T> {
    val list = mutableListOf<T>()
    this.forEach { value ->
        list.add(transform.invoke(value))
    }
    return list.toList()
}

/**
 * Utility function to allow forEach iteration on a [JSONArray]
 */
fun JSONArray.forEach(block: (value: Any) -> Unit) {
    val size = this.length()
    for (idx in 0 until size) {
        this.opt(idx)?.let { value ->
            block(value)
        }
    }
}

/**
 * Utility function to allow forEach with index iteration on a [JSONArray]
 */
fun JSONArray.forEachIndexed(block: (value: Any, index: Int) -> Unit) {
    val size = this.length()
    for (idx in 0 until size) {
        this.opt(idx)?.let { value ->
            block(value, idx)
        }
    }
}