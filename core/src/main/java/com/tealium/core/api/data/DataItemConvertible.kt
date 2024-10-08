package com.tealium.core.api.data

/**
 * Classes that implement this interface should be able to convert themselves successfully to an
 * instance of [DataItem].
 * Typically this can be achieved by using [DataItem.convert].
 *
 * More complex cases can use [DataList] or [DataObject] to represent their properties in a
 * more structured way, and can therefore be recreated if necessary from those data types using
 * a reciprocal [DataItemConverter]
 *
 * @see [DataItem]
 * @see [DataItemConverter]
 */
fun interface DataItemConvertible {

    /**
     * Should return an instance of a [DataItem] that represents all required properties of the
     * implementing class, such that it could be:
     *  - correctly depicted in a JSON format
     *  - fully recreated if necessary using an appropriate [DataItemConverter] implementation
     *
     *  @return A [DataItem] representing the implementing class instance's state
     */
    fun asDataItem(): DataItem
}