package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.mapValuesNotNull
import com.tealium.prism.core.api.settings.CoreSettings
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.internal.misc.Converters
import com.tealium.prism.core.internal.rules.LoadRule
import com.tealium.prism.core.internal.settings.consent.ConsentSettings

data class SdkSettings(
    val core: CoreSettings = CoreSettingsImpl(),
    val modules: Map<String, ModuleSettings> = emptyMap(),
    val loadRules: Map<String, LoadRule> = emptyMap(),
    val transformations: Map<String, TransformationSettings> = emptyMap(),
    val barriers: Map<String, BarrierSettings> = emptyMap(),
    val consent: ConsentSettings? = null
) {

    companion object {
        const val KEY_MODULES = "modules"
        const val KEY_TRANSFORMATIONS = "transformations"
        const val KEY_BARRIERS = "barriers"
        const val KEY_LOAD_RULES = "load_rules"
        const val KEY_CONSENT = "consent"

        fun fromDataObject(dataObject: DataObject): SdkSettings {
            val coreObject = dataObject.requireObject(CoreSettingsImpl.MODULE_NAME)
            val modulesObject = dataObject.requireObject(KEY_MODULES)
            val loadRulesObject = dataObject.requireObject(KEY_LOAD_RULES)
            val transformationsObject = dataObject.requireObject(KEY_TRANSFORMATIONS)
            val barriersObject = dataObject.requireObject(KEY_BARRIERS)

            val core = CoreSettingsImpl.fromDataObject(coreObject)
            val modules = modulesObject.mapValuesNotNull(ModuleSettings.Converter::convert)
            val loadRules = loadRulesObject.mapValuesNotNull(LoadRule.Converter::convert)
            val transformations =
                transformationsObject.mapValuesNotNull(Converters.TransformationSettingsConverter::convert)
            val barriers = barriersObject.mapValuesNotNull(BarrierSettings.Converter::convert)
            val consent = dataObject.get(KEY_CONSENT, ConsentSettings.Converter)

            return SdkSettings(
                core, modules, loadRules, transformations, barriers, consent
            )
        }

        private fun DataObject.requireObject(
            key: String,
            default: DataObject = DataObject.EMPTY_OBJECT
        ): DataObject =
            getDataObject(key) ?: default
    }
}