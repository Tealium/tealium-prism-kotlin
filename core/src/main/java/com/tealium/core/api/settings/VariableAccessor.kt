package com.tealium.core.api.settings

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItemUtils.asDataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataObjectConvertible

data class VariableAccessor(
    val variable: String,
    val path: List<String>? = null
) : DataObjectConvertible {

    override fun asDataObject(): DataObject =
        DataObject.create {
            put(Converter.KEY_VARIABLE, ValueContainer(variable))
            if (path != null) {
                put(Converter.KEY_PATH, path.asDataList())
            }
        }

    object Converter : DataItemConverter<VariableAccessor> {
        const val KEY_VARIABLE = "variable"
        const val KEY_PATH = "path"
        override fun convert(dataItem: DataItem): VariableAccessor? {
            val dataObject = dataItem.getDataObject()
                ?: return null

            val variable = dataObject.get(KEY_VARIABLE, ValueContainer.Converter)
                ?: return null

            val path = dataObject.getDataList(KEY_PATH)
                ?.mapNotNull(DataItem::getString)

            return VariableAccessor(variable.value, path)
        }
    }
}