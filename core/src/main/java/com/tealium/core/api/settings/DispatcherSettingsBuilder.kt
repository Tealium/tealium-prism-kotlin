package com.tealium.core.api.settings

import com.tealium.core.api.data.DataItemUtils.asDataList
import com.tealium.core.api.rules.Rule
import com.tealium.core.internal.settings.MappingsImpl
import com.tealium.core.internal.settings.ModuleSettings

open class DispatcherSettingsBuilder : ModuleSettingsBuilder() {

    /**
     * Sets the [rules] that this [Dispatcher] needs to match in order to dispatch an event
     *
     * The [String] values should be the rule id's that are required, or not, depending the required
     * logic.
     */
    fun setRules(rules: Rule<String>) = apply {
        builder.put(ModuleSettings.KEY_RULES, rules)
    }

    /**
     * Set the mappings for this module.
     *
     * Mappings will only be used if the module is a [Dispatcher].
     * When defined only mapped variables will be passed to the [Dispatcher].
     *
     * Basic usage is very simple:
     * ```kotlin
     * setMappings {
     *   from("input1", "destination1")
     *   from("input2", "destination2")
     * }
     * ```
     *
     * For more complex use cases you can leverage the optional methods on [Mappings.FromOptions]
     * ```kotlin
     * setMappings {
     *  from(VariableAccessor("input1", listOf("container")),
     *        VariableAccessor("destination", listOf("otherContainer")))
     *      .ifInputEquals("value")
     *      .mapTo("otherValue")
     * }
     * ```
     *
     * @param mappings: A lambda used to configure the mappings to be applied to each [Dispatch] before sending it to the `Dispatcher`.
     */
    // TODO - maybe make this nicer for Java users
    fun setMappings(mappings: Mappings.() -> Unit) = apply {
        val operations = MappingsImpl().apply(mappings)
            .build()

        builder.put(ModuleSettings.KEY_MAPPINGS, operations.asDataList())
    }
}