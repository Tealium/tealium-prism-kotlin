package com.tealium.prism.core.api.settings

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.DataObjectConvertible
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.StringContainer
import com.tealium.prism.core.api.data.ValueContainer

/**
 * An object representing the possible configuration options of a mapping.
 *
 * Users are not expected to create instances of this class directly, but mappings should typically
 * be created through the provided builder: [Mappings]
 *
 * @param reference
 *  an optional reference to the key used by this mapping to find the value
 * @param filter
 *  an optional filter, that the [reference] should be equal to in order for the mapping to be applied
 * @param mapTo
 *  a constant value to map instead of the value found at the [reference]
 *
 * @see Mappings
 */
data class MappingParameters(
    val reference: ReferenceContainer?,
    val filter: StringContainer?,
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
            val filter = dataObject.get(KEY_FILTER, StringContainer.Converter)
            val mapTo = dataObject.get(KEY_MAP_TO, ValueContainer.Converter)

            if (reference == null && mapTo == null) return null

            return MappingParameters(reference, filter, mapTo)
        }
    }
}