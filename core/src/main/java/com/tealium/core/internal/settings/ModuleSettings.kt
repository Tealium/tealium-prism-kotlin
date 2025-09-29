package com.tealium.core.internal.settings

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Rule
import com.tealium.core.api.settings.json.TransformationOperation
import com.tealium.core.internal.dispatch.MappingOperation

class ModuleSettings(
    val moduleType: String,
    val moduleId: String = moduleType,
    val order: Int = Int.MAX_VALUE,
    val enabled: Boolean = true,
    val configuration: DataObject = DataObject.EMPTY_OBJECT,
    val rules: Rule<String>? = null,
    val mappings: List<MappingOperation>? = null
) {
    companion object {
        const val KEY_MODULE_ID = "module_id"
        const val KEY_MODULE_TYPE = "module_type"
        const val KEY_ENABLED = "enabled"
        const val KEY_ORDER = "order"
        const val KEY_CONFIGURATION = "configuration"
        const val KEY_RULES = "rules"
        const val KEY_MAPPINGS = "mappings"
    }

    object Converter : DataItemConverter<ModuleSettings> {
        override fun convert(dataItem: DataItem): ModuleSettings? {
            val dataObject = dataItem.getDataObject() ?: return null

            val moduleType = dataObject.getString(KEY_MODULE_TYPE) ?: return null
            val moduleId = dataObject.getString(KEY_MODULE_ID) ?: moduleType

            val enabled = dataObject.getBoolean(KEY_ENABLED)
            val order = dataObject.getInt(KEY_ORDER)
            val configuration = dataObject.getDataObject(KEY_CONFIGURATION)
            val rules = dataObject.get(KEY_RULES, Rule.Converter { item -> item.getString() })
            val mappingsConverter = TransformationOperation.Converter(MappingParameters.Converter)
            val mappings = dataObject.getDataList(KEY_MAPPINGS)?.mapNotNull {
                mappingsConverter.convert(it)
            }

            return ModuleSettings(
                moduleType,
                moduleId,
                order ?: Int.MAX_VALUE,
                enabled ?: true,
                configuration ?: DataObject.EMPTY_OBJECT,
                rules,
                mappings
            )
        }
    }
}