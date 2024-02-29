package com.tealium.core.api.settings

import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumSerializable
import com.tealium.core.internal.settings.ModuleSettingsImpl

/**
 * Base class to extend from when creating a settings builder for a new module.
 *
 * @param moduleName This should match the name of the [Module] and the [ModuleFactory]
 */
abstract class ModuleSettingsBuilder(
    val moduleName: String
) {
    protected val builder: TealiumBundle.Builder = TealiumBundle.Builder()
    private val dependencies: MutableList<Any> = mutableListOf()

    /**
     * Adds a dependency that would otherwise not be possible to serialize through [TealiumSerializable]
     * into a [TealiumBundle].
     *
     * @param dependency The non-serializable dependency to add.
     */
    fun addDependency(dependency: Any) {
        dependencies.add(dependency)
    }

    /**
     * Returns the complete [ModuleSettings] as configured by this [ModuleSettingsBuilder].
     */
    fun build() : ModuleSettings {
        return ModuleSettingsImpl(
            bundle = builder.getBundle(),
            dependencies = dependencies.toList()
        )
    }
}