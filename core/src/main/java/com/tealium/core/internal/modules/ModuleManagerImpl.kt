package com.tealium.core.internal.modules

import com.tealium.core.TealiumContext
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleSettings
import com.tealium.core.internal.SdkSettings

class ModuleManagerImpl(
    factories: List<ModuleFactory>,
    private val context: TealiumContext,
    private var settings: SdkSettings
) {
    val moduleFactories: Map<String, ModuleFactory> = factories.associateBy { it.name }

    val modules = moduleFactories.map {
        it.value.create(context, settings.moduleSettings[it.key] ?: ModuleSettingImpl())
    }

    fun <T> getModulesOfType(clazz: Class<T>): List<T> {
        return modules.filterIsInstance(clazz)
    }
}

class ModuleSettingImpl(
    override val enabled: Boolean = true,
    override val settings: Map<String, Any> = emptyMap()
) : ModuleSettings