package com.tealium.prism.core.api.settings.modules

import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.settings.Mappings
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.settings.ModuleSettings

/**
 * A settings builder class to support building of settings relevant to [Dispatcher] implementations.
 */
open class DispatcherSettingsBuilder<M : Mappings, T : DispatcherSettingsBuilder<M, T>>(
    moduleType: String,
    private val mappingsSupplier: () -> M
) : RuleModuleSettingsBuilder<T>(moduleType) {

    /**
     * Set the mappings for this module.
     *
     * Mappings will only be used if the module is a [Dispatcher].
     * When defined only mapped variables will be passed to the [Dispatcher].
     *
     * Basic usage is very simple:
     * ```kotlin
     * setMappings {
     *   mapFrom("input1", "destination1")
     *   mapFrom("input2", "destination2")
     * }
     * ```
     *
     * For more complex use cases you can leverage the optional methods on [Mappings.VariableOptions]
     * ```kotlin
     * setMappings {
     *  mapFrom(JsonPath["container"]["input1"], JsonPath["otherContainer"]["destination"])
     *      .ifValueEquals("value")
     * }
     * ```
     *
     * @param mappings: A lambda used to configure the mappings to be applied to each [Dispatch] before sending it to the [Dispatcher].
     */
    @Suppress("UNCHECKED_CAST")
    @JvmSynthetic
    fun setMappings(mappings: M.() -> Unit): T = apply {
        val operations = mappingsSupplier().apply(mappings)
            .build()

        moduleSettings.put(ModuleSettings.KEY_MAPPINGS, operations.asDataList())
    } as T

    /**
     * Set the mappings for this module.
     *
     * Mappings will only be used if the module is a [Dispatcher].
     * When defined only mapped variables will be passed to the [Dispatcher].
     *
     * Basic usage is very simple:
     * ```java
     * setMappings(m -> {
     *   m.mapFrom("input1", "destination1");
     *   m.mapFrom("input2", "destination2");
     * });
     * ```
     *
     * For more complex use cases you can leverage the optional methods on [Mappings.VariableOptions]
     * ```java
     * setMappings(m -> {
     *   m.mapFrom(m.path("container").key("input1"), m.path("otherContainer").key("destination"))
     *     .ifValueEquals("value");
     * });
     * ```
     *
     * @param mappings: A lambda used to configure the mappings to be applied to each [Dispatch] before sending it to the [Dispatcher].
     */
    fun setMappings(mappings: Callback<M>): T =
        setMappings { mappings.onComplete(this) }
}