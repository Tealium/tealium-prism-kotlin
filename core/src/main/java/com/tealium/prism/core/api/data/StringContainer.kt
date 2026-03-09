package com.tealium.prism.core.api.data

/**
 * A container for holding a specific [String] [value].
 *
 * This class is typically used for retrieving data from settings as it will serialize and deserialize
 * to/from a [DataObject], as opposed to just the [value] string.
 */
data class StringContainer(
    val value: String
): DataObjectConvertible {

    override fun asDataObject(): DataObject =
        DataObject.create {
            put(Converter.KEY_VALUE, value)
        }

    object Converter : DataItemConverter<StringContainer> {
        const val KEY_VALUE = "value"
        override fun convert(dataItem: DataItem): StringContainer? {
            val dataObject = dataItem.getDataObject()
                ?: return null

            val value = dataObject.getString(KEY_VALUE)
                ?: return null

            return StringContainer(value)
        }
    }
}