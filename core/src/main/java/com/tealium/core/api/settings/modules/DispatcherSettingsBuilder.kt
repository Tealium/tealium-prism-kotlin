package com.tealium.core.api.settings.modules

import com.tealium.core.api.data.DataItemUtils.asDataList
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.settings.Mappings
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.internal.settings.MappingsImpl
import com.tealium.core.internal.settings.ModuleSettings

/**
 * A settings builder class to support building of settings relevant to [Dispatcher] implementations.
 */
open class DispatcherSettingsBuilder<T : DispatcherSettingsBuilder<T>>(moduleType: String) :
    RuleModuleSettingsBuilder<T>(moduleType) {

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
     * For more complex use cases you can leverage the optional methods on [Mappings.VariableOptions]
     * ```kotlin
     * setMappings {
     *  from(VariableAccessor("input1", listOf("container")),
     *        VariableAccessor("destination", listOf("otherContainer")))
     *      .ifValueEquals("value")
     * }
     * ```
     *
     * @param mappings: A lambda used to configure the mappings to be applied to each [Dispatch] before sending it to the [Dispatcher].
     */
    // TODO - maybe make this nicer for Java users
    @Suppress("UNCHECKED_CAST")
    fun setMappings(mappings: Mappings.() -> Unit): T = apply {
        val operations = MappingsImpl().apply(mappings)
            .build()

        moduleSettings.put(ModuleSettings.KEY_MAPPINGS, operations.asDataList())
    } as T
}