package com.tealium.core.api.settings

import com.tealium.core.api.rules.Rule
import com.tealium.core.internal.settings.ModuleSettings

open class CollectorSettingsBuilder<T: CollectorSettingsBuilder<T>> : ModuleSettingsBuilder<T>() {

    /**
     * Sets the [rules] that this [Collector] needs to match in order to collect data for an event
     *
     * The [String] values should be the rule id's that are required, or not, depending the required
     * logic.
     */
    @Suppress("UNCHECKED_CAST")
    fun setRules(rules: Rule<String>): T = apply {
        builder.put(ModuleSettings.KEY_RULES, rules)
    } as T
}