package com.tealium.core.internal.settings

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataObjectConvertible
import com.tealium.core.api.settings.ValueContainer
import com.tealium.core.api.settings.VariableAccessor

data class MappingParameters(
    val key: VariableAccessor?,
    val filter: ValueContainer?,
    val mapTo: ValueContainer?
) : DataObjectConvertible {

    override fun asDataObject(): DataObject =
        DataObject.create {
            if (key != null) {
                put(Converter.KEY_KEY, key)
            }
            if (filter != null) {
                put(Converter.KEY_FILTER, filter)
            }
            if (mapTo != null) {
                put(Converter.KEY_MAP_TO, mapTo)
            }
        }

    object Converter : DataItemConverter<MappingParameters> {
        const val KEY_KEY = "key"
        const val KEY_FILTER = "filter"
        const val KEY_MAP_TO = "map_to"

        override fun convert(dataItem: DataItem): MappingParameters? {
            val dataObject = dataItem.getDataObject()
                ?: return null

            val key = dataObject.get(KEY_KEY, VariableAccessor.Converter)
            val filter = dataObject.get(KEY_FILTER, ValueContainer.Converter)
            val mapTo = dataObject.get(KEY_MAP_TO, ValueContainer.Converter)

            if (key == null && mapTo == null) return null

            return MappingParameters(key, filter, mapTo)
        }
    }
}