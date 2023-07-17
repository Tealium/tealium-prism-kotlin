package com.tealium.core.api


interface Serde<T, R> {
    val serializer: Serializer<T, R>
    val deserializer: Deserializer<R, T>
}

/**
 * Transforms an input object of type [R] to an output object of type [T]
 */
interface Serializer<T, R> {
    /**
     * From the input [value] of type [T], this method should produce an output
     * of type [R]
     *
     * @param value The value to use to compute the result
     * @return The new instance of type [R]
     */
    fun serialize(value: T): R
}

/**
 * Transforms an input object of type [T] to an output object of type [R]
 */
interface Deserializer<T, R> {
    /**
     * From the input [value] of type [T], this method should produce an output
     * of type [R]
     *
     * @param value The value to use to compute the result
     * @return The new instance of type [R]
     */
    fun deserialize(value: T): R
}