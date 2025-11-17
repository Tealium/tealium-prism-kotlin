package com.tealium.prism.core.api.data

data class ValueContainer(
    val value: String
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

            val value = dataObject.getString(KEY_VALUE)
                ?: return null

            return ValueContainer(value)
        }
    }
}