package com.tealium.core.api.data.bundle

/**
 * Classes that implement this interface should be able to convert themselves successfully to an
 * instance of [TealiumValue].
 * Typically this can be achieved by using [TealiumValue.convert].
 *
 * More complex cases can use [TealiumList] or [TealiumBundle] to represent their properties in a
 * more structured way, and can therefore be recreated if necessary from those data types using
 * a reciprocal [TealiumDeserializable]
 *
 * @see [TealiumValue]
 * @see [TealiumDeserializable]
 */
interface TealiumSerializable {

    /**
     * Should return an instance of a [TealiumValue] that represents all required properties of the
     * implementing class, such that it could be:
     *  - correctly depicted in a JSON format
     *  - fully recreated if necessary using an appropriate [TealiumDeserializable] implementation
     *
     *  @return A [TealiumValue] representing the implementing class instance's state
     */
    fun asTealiumValue(): TealiumValue
}