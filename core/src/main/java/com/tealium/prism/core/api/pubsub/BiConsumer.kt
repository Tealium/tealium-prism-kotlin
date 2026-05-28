package com.tealium.prism.core.api.pubsub

/**
 * Reimplementation of the Java SDK's [java.util.function.BiConsumer] that isn't supported across all
 * Android API levels.
 */
fun interface BiConsumer<T, R> {

    /**
     * Method to receive the values to consume.
     */
    fun accept(t: T, r: R)
}
