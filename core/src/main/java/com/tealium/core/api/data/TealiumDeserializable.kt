package com.tealium.core.api.data

import com.tealium.core.api.Deserializer

/**
 * Classes that implement this interface should be able to reconstruct an object of type [T] from
 * a given [TealiumValue] - on the assumption that the [TealiumValue] does accurately describe all
 * components required to create a new instance of type [T]
 *
 * This is typically a reciprocal implementation of a [TealiumSerializable]; such that semi-complex
 * types can be accurately described by a structured format such as [TealiumList] or
 * [TealiumBundle] using an instance of a [TealiumSerializable] and subsequently recreated if
 * necessary using an the appropriate implementation of [TealiumDeserializable]
 *
 * @see TealiumSerializable
 */
interface TealiumDeserializable<T> : Deserializer<TealiumValue, T?> {

    /**
     * Should return an instance of [T] using the data provided by [value].
     *
     * There are no guarantees for which underlying data type will be provided to [deserialize] as a
     * [TealiumValue] - implementations should request the appropriate type, and return null if it
     * is not possible to create an instance of [T] from the [TealiumValue] provided.
     *
     * @return Reconstructed instance of [T]
     * @see TealiumSerializable
     */
    override fun deserialize(value: TealiumValue): T?
}