package com.tealium.prism.extensions.internal.setdatavalues

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.DataObjectConvertible
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueSource

data class SetDataValuesOperation(
    val input: ValueSource,
    val destination: ReferenceContainer
) : DataObjectConvertible {
    override fun asDataObject(): DataObject =
        DataObject.create {
            put(Converter.KEY_DESTINATION, destination)
            put(Converter.KEY_INPUT, input)
        }

    object Converter : DataItemConverter<SetDataValuesOperation> {
        const val KEY_DESTINATION = "destination"
        const val KEY_INPUT = "input"

        override fun convert(dataItem: DataItem): SetDataValuesOperation? {
            val obj = dataItem.getDataObject() ?: return null
            val destination = obj.get(KEY_DESTINATION, ReferenceContainer.Converter) ?: return null
            val input = obj.get(KEY_INPUT, ValueSource.Converter) ?: return null

            return SetDataValuesOperation(
                input,
                destination
            )
        }
    }
}