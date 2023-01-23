package com.tealium.core.internal.modules

import com.tealium.core.TealiumContext
import com.tealium.core.api.CoreSettings
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleSettings
import com.tealium.core.api.listeners.ModuleSettingsUpdatedListener
import com.tealium.core.api.listeners.SettingsUpdatedListener
import com.tealium.core.internal.SdkSettings

class ModuleManagerImpl(
    factories: List<ModuleFactory>,
    private val context: TealiumContext,
    private var settings: SdkSettings
) : SettingsUpdatedListener {
    private val moduleFactories: Map<String, ModuleFactory> = factories.associateBy { it.name }

    private var modules: Map<String, Module> = moduleFactories.map {
        it.value.create(context, settings.moduleSettings[it.key] ?: ModuleSettingImpl())
    }
        .filterNotNull()
        .associateBy { it.name }

    fun <T> getModulesOfType(clazz: Class<T>): List<T> {
        return modules.values.filterIsInstance(clazz)
    }

    fun <T> getModuleOfType(clazz: Class<T>): T? {
        return getModulesOfType(clazz).first()
    }

    override fun onSettingsUpdated(
        coreSettings: CoreSettings,
        moduleSettings: Map<String, ModuleSettings>
    ) {
        // existing modules
        val newModules: MutableMap<String, Module> = modules.values.filter { module ->
            // remove disabled
            val settings = moduleSettings[module.name]
            settings == null
                    || settings.enabled
        }.map {
            // updated
            if (it is ModuleSettingsUpdatedListener) {
                it.onModuleSettingsUpdated(
                    coreSettings,
                    moduleSettings[it.name] ?: ModuleSettingImpl()
                )
            }
            it
        }.associateBy { it.name }
            .toMutableMap()

        // factories for missing.
        moduleFactories.filter { (name, _) ->
            !newModules.containsKey(name) // not already instantiated
        }.forEach { (name, factory) ->
            //
            val settings = moduleSettings[name] ?: ModuleSettingImpl()
            if (!settings.enabled) return@forEach

            factory.create(context, settings)?.let { module ->
                newModules[module.name] = module
            }
        }

        modules = newModules.toMap()
    }
}

class ModuleSettingImpl(
    override val enabled: Boolean = true,
    override val settings: Map<String, Any> = emptyMap()
) : ModuleSettings