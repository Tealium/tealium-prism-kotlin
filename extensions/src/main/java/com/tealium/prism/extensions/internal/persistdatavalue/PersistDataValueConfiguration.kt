package com.tealium.prism.extensions.internal.persistdatavalue

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueSource
import com.tealium.prism.core.api.misc.ExpiryPolicy
import com.tealium.prism.extensions.api.persistdatavalue.UpdatePolicy

class PersistDataValueConfiguration(
    val expiryPolicy: ExpiryPolicy,
    val updatePolicy: UpdatePolicy,
    val input: ValueSource,
    val destination: ReferenceContainer
) {

    object Converter : DataItemConverter<PersistDataValueConfiguration> {
        const val KEY_INPUT = "input"
        const val KEY_DESTINATION = "destination"
        const val KEY_DURATION = "duration"
        const val KEY_UPDATE_POLICY = "update_policy"

        override fun convert(dataItem: DataItem): PersistDataValueConfiguration? {
            val dataObject = dataItem.getDataObject() ?: return null

            val input = dataObject.get(KEY_INPUT, ValueSource.Converter) ?: return null
            val destination =
                dataObject.get(KEY_DESTINATION, ReferenceContainer.Converter) ?: return null
            val expiryPolicy = dataObject.get(KEY_DURATION, ExpiryPolicy.Converter)
                ?: ExpiryPolicy.SESSION
            val updatePolicy = dataObject.get(KEY_UPDATE_POLICY, UpdatePolicy.Converter)
                ?: UpdatePolicy.ALLOW_UPDATE

            return PersistDataValueConfiguration(expiryPolicy, updatePolicy, input, destination)
        }
    }
}