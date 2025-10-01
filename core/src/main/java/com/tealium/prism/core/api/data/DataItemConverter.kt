package com.tealium.prism.core.api.data

/**
 * Classes that implement this interface should be able to reconstruct an object of type [T] from
 * a given [DataItem] - on the assumption that the [DataItem] does accurately describe all
 * components required to create a new instance of type [T]
 *
 * This is typically a reciprocal implementation of a [DataItemConvertible]; such that semi-complex
 * types can be accurately described by a structured format such as [DataList] or
 * [DataObject] using an instance of a [DataItemConvertible] and subsequently recreated if
 * necessary using an the appropriate implementation of [DataItemConverter]
 *
 * @see DataItemConvertible
 */
fun interface DataItemConverter<T> {

    /**
     * Should return an instance of [T] using the data provided by [dataItem].
     *
     * There are no guarantees for which underlying data type will be provided to [convert] as a
     * [DataItem] - implementations should request the appropriate type, and return null if it
     * is not possible to create an instance of [T] from the [DataItem] provided.
     *
     * @return Reconstructed instance of [T]
     * @see DataItemConvertible
     */
    fun convert(dataItem: DataItem): T?
}