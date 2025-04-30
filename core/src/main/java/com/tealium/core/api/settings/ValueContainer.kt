package com.tealium.core.api.settings

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataObjectConvertible

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