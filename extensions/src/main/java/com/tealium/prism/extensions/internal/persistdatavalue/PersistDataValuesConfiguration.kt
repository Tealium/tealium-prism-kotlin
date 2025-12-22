package com.tealium.prism.extensions.internal.persistdatavalue

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.extensions.internal.ValueSource

class PersistDataValuesConfiguration(
    val input: ValueSource,
    val expiry: Expiry = Expiry.SESSION,
    val updateType: PersistValuesUpdateType = PersistValuesUpdateType.KEEP_FIRST_VALUE
) : DataItemConvertible {
    override fun asDataItem(): DataItem {
        val input = when (input) {
            is ValueSource.Reference -> input.reference.asDataItem()
            is ValueSource.Constant -> input.value.asDataItem()
        }

        return DataObject.create {
            put(Convert.KEY_INPUT, input)
            put(Convert.KEY_EXPIRY, expiry.expiryTime())
            put(Convert.KEY_UPDATE_TYPE, updateType.asDataItem())
        }.asDataItem()

    }

    object Convert : DataItemConverter<PersistDataValuesConfiguration> {
        const val KEY_INPUT = "input"
        const val KEY_EXPIRY = "expiry"
        const val KEY_UPDATE_TYPE = "update_type"

        override fun convert(dataItem: DataItem): PersistDataValuesConfiguration? {
            val dataObject = dataItem.getDataObject() ?: return null

            val input = dataObject.getDataObject(KEY_INPUT)?.asDataItem() ?: return null
            val expiration =
                dataObject.getLong(KEY_EXPIRY)?.let { Expiry.fromLongValue(it) } ?: Expiry.SESSION
            val updateType = dataObject.get(KEY_UPDATE_TYPE, PersistValuesUpdateType.Converter)
                ?: PersistValuesUpdateType.KEEP_FIRST_VALUE

            val referenceContainer = ReferenceContainer.Converter.convert(input)
            if (referenceContainer != null)
                return PersistDataValuesConfiguration(
                    ValueSource.Reference(referenceContainer),
                    expiration,
                    updateType
                )

            val valueContainer = ValueContainer.Converter.convert(input)
            if (valueContainer != null)
                return PersistDataValuesConfiguration(
                    ValueSource.Constant(valueContainer),
                    expiration,
                    updateType
                )

            return null
        }
    }
}