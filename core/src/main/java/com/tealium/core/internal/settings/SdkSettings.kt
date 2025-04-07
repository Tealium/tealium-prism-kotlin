package com.tealium.core.internal.settings

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.settings.CoreSettings
import com.tealium.core.internal.rules.LoadRule

data class SdkSettings(
    val core: CoreSettings = CoreSettingsImpl(),
    val modules: Map<String, ModuleSettings> = emptyMap(),
    val loadRules: Map<String, LoadRule> = emptyMap()
    // TODO - transformations
    // TODO - barriers
) {

    companion object {
        const val KEY_MODULES = "modules"
        const val KEY_TRANSFORMATIONS = "transformations"
        const val KEY_BARRIERS = "barriers"
        const val KEY_LOAD_RULES = "load_rules"

        fun fromDataObject(dataObject: DataObject): SdkSettings {
            val coreObject = dataObject.requireObject(CoreSettingsImpl.MODULE_NAME)
            val modulesObject = dataObject.requireObject(KEY_MODULES)
            val loadRulesObject = dataObject.requireObject(KEY_LOAD_RULES)
            // TODO - transformations
            // TODO - barriers

            val core = CoreSettingsImpl.fromDataObject(coreObject)
            val modules = modulesObject.mapValuesNotNull(ModuleSettings.Converter::convert)
            val loadRules = loadRulesObject.mapValuesNotNull(LoadRule.Converter::convert)

            return SdkSettings(core, modules, loadRules)
        }

        private fun DataObject.requireObject(
            key: String,
            default: DataObject = DataObject.EMPTY_OBJECT
        ): DataObject =
            getDataObject(key) ?: default

        private inline fun <T> DataObject.mapValuesNotNull(transform: (DataItem) -> T?): Map<String, T> =
            mapNotNull { (key, value) ->
                val transformed = transform(value) ?: return@mapNotNull null
                key to transformed
            }.toMap()
    }
}