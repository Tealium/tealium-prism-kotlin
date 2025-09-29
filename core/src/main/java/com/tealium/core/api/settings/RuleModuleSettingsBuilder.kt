package com.tealium.core.api.settings

import com.tealium.core.api.modules.Module
import com.tealium.core.api.rules.Rule
import com.tealium.core.internal.settings.ModuleSettings

/**
 * A settings builder that enables the assignment of [Rule]'s to the [Module]'s capabilities.
 */
open class RuleModuleSettingsBuilder<T : RuleModuleSettingsBuilder<T>>(moduleType: String) :
    ModuleSettingsBuilder<T>(moduleType) {

    /**
     * Sets the [rules] that this [Module] needs to match in order to process an event (e.g. collection
     * or dispatching)
     *
     * The [String] values should be the rule id's that are required, or not, depending on the required
     * logic.
     */
    @Suppress("UNCHECKED_CAST")
    fun setRules(rules: Rule<String>): T = apply {
        moduleSettings.put(ModuleSettings.KEY_RULES, rules)
    } as T
}