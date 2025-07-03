package com.tealium.core.internal.settings.consent

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.mapValuesNotNull

data class ConsentSettings(
    val configurations: Map<String, ConsentConfiguration>
) {

    object Converter : DataItemConverter<ConsentSettings> {
        const val KEY_CONFIGURATIONS = "configurations"

        override fun convert(dataItem: DataItem): ConsentSettings? {
            val configurationsObject = dataItem.getDataObject()
                ?.getDataObject(KEY_CONFIGURATIONS)
                ?: return null

            val configurations =
                configurationsObject.mapValuesNotNull(ConsentConfiguration.Converter::convert)

            return ConsentSettings(configurations)
        }
    }
}