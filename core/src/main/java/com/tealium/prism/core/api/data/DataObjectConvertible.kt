package com.tealium.prism.core.api.data

/**
 * Classes that implement this interface should be able to convert themselves successfully to an
 * instance of [DataObject].
 *
 * @see [DataObject]
 * @see [DataItemConvertible]
 */
fun interface DataObjectConvertible: DataItemConvertible {

    /**
     * Should return an instance of a [asDataObject] that represents all required properties of the
     * implementing class, such that it could be:
     *  - correctly depicted in a JSON format
     *  - fully recreated if necessary using an appropriate [DataItemConverter] implementation
     *
     *  @return A [DataObject] representing the implementing class instance's state
     */
    fun asDataObject(): DataObject

    override fun asDataItem(): DataItem =
        asDataObject().asDataItem()
}