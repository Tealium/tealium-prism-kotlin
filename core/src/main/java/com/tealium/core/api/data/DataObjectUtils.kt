package com.tealium.core.api.data

/**
 * Maps the [Map.Entry.value] component only, keeping the [Map.Entry.key] the same.
 *
 * `null` values returned by the [transform] are dropped from the returned Map.
 */
inline fun <T> DataObject.mapValuesNotNull(transform: (DataItem) -> T?): Map<String, T> =
    mapNotNull { (key, value) ->
        val transformed = transform(value) ?: return@mapNotNull null
        key to transformed
    }.toMap()

/**
 * Maps the [Map.Entry.value] component only, keeping the [Map.Entry.key] the same.
 *
 * `null` values returned by the [transform] are dropped from the returned Map.
 */
inline fun <T> DataObject.mapValues(transform: (DataItem) -> T): Map<String, T> =
    associate { (key, value) ->
        key to transform(value)
    }