package com.tealium.core.api.listeners

/**
 * Generic callback interface for unknown types, where the results are synchronized on the [Tealium]
 * background processing thread.
 */
fun interface TealiumCallback<T> {

    /**
     * Notification method to receive the result, delivered on the [Tealium] background processing
     * thread.
     *
     * @param result
     */
    fun onComplete(result: T)
}