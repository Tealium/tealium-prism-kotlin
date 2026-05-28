package com.tealium.prism.extensions.internal.setdatavalues

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter

class SetDataValuesConfiguration(
    val operations: List<SetDataValuesOperation>
) {

    object Converter : DataItemConverter<SetDataValuesConfiguration> {
        const val KEY_OPERATIONS = "operations"

        private val converter = SetDataValuesOperation.Converter
        override fun convert(dataItem: DataItem): SetDataValuesConfiguration? {
            val obj = dataItem.getDataObject() ?: return null
            val operations = obj.getDataList(KEY_OPERATIONS) ?: return null
            val convertedOperations = operations.mapNotNull(converter::convert)

            return SetDataValuesConfiguration(convertedOperations)
        }
    }
}