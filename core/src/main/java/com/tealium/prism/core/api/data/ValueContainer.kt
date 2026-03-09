package com.tealium.prism.core.api.data

/**
 * A container for holding a specific [DataItem] [value].
 *
 * This class is typically used for retrieving data from settings as it will serialize and deserialize
 * to/from a [DataObject], as opposed to just the [value].
 */
data class ValueContainer(
    val value: DataItem
): DataObjectConvertible {

    override fun asDataObject(): DataObject =
        DataObject.create {
            put(Converter.KEY_VALUE, value)
        }

    object Converter : DataItemConverter<ValueContainer> {
        const val KEY_VALUE = "value"
        override fun convert(dataItem: DataItem): ValueContainer? {
            val dataObject = dataItem.getDataObject()
                ?: return null

            val value = dataObject.get(KEY_VALUE)
                ?: return null

            return ValueContainer(value)
        }
    }
}