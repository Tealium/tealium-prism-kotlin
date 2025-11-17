package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.DataObjectConvertible
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueContainer

data class MappingParameters(
    val reference: ReferenceContainer?,
    val filter: ValueContainer?,
    val mapTo: ValueContainer?
) : DataObjectConvertible {

    override fun asDataObject(): DataObject =
        DataObject.create {
            if (reference != null) {
                put(Converter.KEY_REFERENCE, reference)
            }
            if (filter != null) {
                put(Converter.KEY_FILTER, filter)
            }
            if (mapTo != null) {
                put(Converter.KEY_MAP_TO, mapTo)
            }
        }

    object Converter : DataItemConverter<MappingParameters> {
        const val KEY_REFERENCE = "reference"
        const val KEY_FILTER = "filter"
        const val KEY_MAP_TO = "map_to"

        override fun convert(dataItem: DataItem): MappingParameters? {
            val dataObject = dataItem.getDataObject()
                ?: return null

            val reference = dataObject.get(KEY_REFERENCE, ReferenceContainer.Converter)
            val filter = dataObject.get(KEY_FILTER, ValueContainer.Converter)
            val mapTo = dataObject.get(KEY_MAP_TO, ValueContainer.Converter)

            if (reference == null && mapTo == null) return null

            return MappingParameters(reference, filter, mapTo)
        }
    }
}