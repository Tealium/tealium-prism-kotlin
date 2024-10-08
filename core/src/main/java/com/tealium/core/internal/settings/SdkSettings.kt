package com.tealium.core.internal.settings

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.settings.CoreSettings

data class SdkSettings(
    val moduleSettings: Map<String, DataObject> = emptyMap()
) : DataItemConvertible {

    val coreSettings: CoreSettings
        get() = moduleSettings[CoreSettingsImpl.MODULE_NAME]?.let {
            CoreSettingsImpl.fromDataObject(it)
        } ?: CoreSettingsImpl()

    override fun asDataItem(): DataItem {
        return DataObject.create {
            for (settings in moduleSettings) {
                put(settings.key, settings.value)
            }

        }.asDataItem()
    }

    object Converter : DataItemConverter<SdkSettings> {
        override fun convert(dataItem: DataItem): SdkSettings? {
            val dataObject = dataItem.getDataObject() ?: return null

            val modulesSettings: Map<String, DataObject> = dataObject.associate { entry ->
                val entryDataObject = entry.value.getDataObject()
                entry.key to (entryDataObject ?: DataObject.EMPTY_OBJECT)
            }

            return SdkSettings(
                moduleSettings = modulesSettings,
            )
        }
    }
}