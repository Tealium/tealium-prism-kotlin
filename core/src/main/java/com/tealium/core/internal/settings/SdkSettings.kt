package com.tealium.core.internal.settings

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.settings.CoreSettings

data class SdkSettings(
    val core: CoreSettings = CoreSettingsImpl(),
    val modules: Map<String, ModuleSettings> = emptyMap()
    // TODO - transformations
    // TODO - barriers
    // TODO - loadRules
) {

    companion object {
        const val KEY_MODULES = "modules"
        const val KEY_TRANSFORMATIONS = "transformations"
        const val KEY_BARRIERS = "barriers"
        const val KEY_LOAD_RULES = "load_rules"

        fun fromDataObject(dataObject: DataObject): SdkSettings {
            val coreObject = dataObject.requireObject(CoreSettingsImpl.MODULE_NAME)
            val modulesObject = dataObject.requireObject(KEY_MODULES)
            // TODO - transformations
            // TODO - barriers
            // TODO - loadRules

            val core = CoreSettingsImpl.fromDataObject(coreObject)
            val modules = modulesObject.mapDataObjects { value -> ModuleSettings(value) }

            return SdkSettings(core, modules)
        }

        private fun DataObject.requireObject(
            key: String,
            default: DataObject = DataObject.EMPTY_OBJECT
        ): DataObject =
            getDataObject(key) ?: default

        private inline fun <T> DataObject.mapDataObjects(transform: (DataObject) -> T): Map<String, T> =
            associate { (key, value) ->
                val obj = value.getDataObject()
                    ?: DataObject.EMPTY_OBJECT

                key to transform(obj)
            }
    }
}