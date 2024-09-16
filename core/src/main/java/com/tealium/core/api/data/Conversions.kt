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
fun <T> JSONArray.map(transform: (value: Any) -> T): List<T> {
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

/**
 * Merges two [TealiumBundle] objects together.
 *
 * Keys from the right-hand-side (rhs) of this operator will be preferred over the left-hand-side (lhs).
 * In all cases, key clashes will simply take the value from the right-hand-side.
 *
 * e.g.
 * ```kotlin
 * val lhs = TealiumBundle.create {
 *     put("string", "value")
 *     put("int", 1)
 *     put("bundle", TealiumBundle.create {
 *         put("key1", "string")
 *         put("key2", true)
 *     })
 * }
 *
 * val rhs = TealiumBundle.create {
 *     put("string", "new value")
 *     put("bundle", TealiumBundle.create {
 *         put("key1", "new string")
 *         put("key3", "extra string")
 *     })
 * }
 *
 * val merged = lhs + rhs
 *
 * // merged will be the equivalent of this:
 * TealiumBundle.create {
 *     put("string", "new value")            // from rhs
 *     put("int", 1)                         // from lhs
 *     put("bundle", TealiumBundle.create {
 *         put("key1", "new string")         // from rhs
 *         put("key3", "extra string")       // from rhs
 *     })
 * }
 * ```
 *
 * @param other The incoming values to merge into this [TealiumBundle]
 * @return A new [TealiumBundle] that contains the merged key-value pairs from both [TealiumBundle]s
 */
operator fun TealiumBundle.plus(other: TealiumBundle): TealiumBundle {
    return this.copy {
        for ((key, value) in other) {
            put(key, value)
        }
    }
}