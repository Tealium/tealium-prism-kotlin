package com.tealium.prism.core.api.pubsub

/**
 * Reimplementation of the Java SDK's [java.util.function.Consumer] that isn't supported across all
 * Android API levels.
 */
fun interface Consumer<T> {

    /**
     * Method to receive the values to consume.
     */
    fun accept(value: T)
}
