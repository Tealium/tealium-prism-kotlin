package com.tealium.prism.core.api.data

/**
 * Transforms an input object of type [T] to an output object of type [R]
 */
fun interface Deserializer<T, R> {
    /**
     * From the input [value] of type [T], this method should produce an output
     * of type [R]
     *
     * @param value The value to use to compute the result
     * @return The new instance of type [R]
     */
    fun deserialize(value: T): R
}