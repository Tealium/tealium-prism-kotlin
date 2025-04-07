package com.tealium.core.internal.settings

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Rule

class ModuleSettings(
    val enabled: Boolean = true,
    val configuration: DataObject = DataObject.EMPTY_OBJECT,
    val rules: Rule<String>? = null
) {
    // TODO - mappings: List<Mappings>?

    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_CONFIGURATION = "configuration"
        const val KEY_RULES = "rules"
        const val KEY_MAPPINGS = "mappings"
    }

    object Converter : DataItemConverter<ModuleSettings> {
        override fun convert(dataItem: DataItem): ModuleSettings? {
            val dataObject = dataItem.getDataObject() ?: return null

            val enabled = dataObject.getBoolean(KEY_ENABLED)
            val configuration = dataObject.getDataObject(KEY_CONFIGURATION)
            val rules = dataObject.get(KEY_RULES, Rule.Converter { item -> item.getString() })

            return ModuleSettings(
                enabled ?: true,
                configuration ?: DataObject.EMPTY_OBJECT,
                rules
            )
        }
    }
}