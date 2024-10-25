package com.tealium.core.internal.logger

import com.tealium.core.api.tracking.Dispatch

/**
 * Returns an [Exception]'s message, or the [default] provided.
 *
 * If no [default] is provided then the string "null" will be returned.
 */
fun Exception.nonNullMessage(default: String = "null"): String {
    return message ?: default
}

/**
 * Maps a [Collection] of [Dispatch]es to a list of their [Dispatch.logDescription] values
 */
fun Collection<Dispatch>.logDescriptions(): List<String> =
    map(Dispatch::logDescription)

/**
 * Maps a [Collection] of [Dispatch]es to a list of their [Dispatch.id] values
 */
fun Collection<Dispatch>.ids(): List<String> =
    map(Dispatch::id)
