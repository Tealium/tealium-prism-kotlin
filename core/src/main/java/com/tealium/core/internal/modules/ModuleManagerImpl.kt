package com.tealium.core.internal.modules

import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.internal.settings.SdkSettings
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.internal.settings.ModuleSettingsImpl

class ModuleManagerImpl(
    moduleFactories: List<ModuleFactory>,
    private val scheduler: Scheduler,
    private val modulesSubject: StateSubject<Set<Module>> = Observables.stateSubject(setOf())
) : InternalModuleManager {

    private val moduleFactories: MutableList<ModuleFactory> = moduleFactories.toMutableList()
    private var _modules: Map<String, Module> = emptyMap()

    override val modules: ObservableState<Set<Module>>
        get() = modulesSubject.asObservableState()

    override fun addModuleFactory(vararg moduleFactory: ModuleFactory) {
        moduleFactories.addAll(moduleFactory)
    }

    override fun <T> getModulesOfType(clazz: Class<T>): List<T> {
        return _modules.values.filterIsInstance(clazz)
    }

    override fun <T> getModuleOfType(clazz: Class<T>): T? {
        for (module in _modules.values) {
            if (clazz.isInstance(module)) {
                return clazz.cast(module)
            }
        }

        return null
    }

    override fun <T> getModuleOfType(clazz: Class<T>, callback: TealiumCallback<T?>) {
        scheduler.execute {
            callback.onComplete(getModuleOfType(clazz))
        }
    }

    override fun updateModuleSettings(context: TealiumContext, settings: SdkSettings) {
        // iterate all factories to preserve insertion order
        val oldModules = _modules
        val newModules = mutableMapOf<String, Module>()
        for (factory in moduleFactories) {
            val oldModule = oldModules[factory.name]
            val newModule = if (oldModule != null) {
                // update all existing module settings in case disabled modules need to shut
                // anything down
                val updatedModuleSettings = settings.moduleSettings.getOrDefault(factory.name)

                context.logger.trace?.log(factory.name, "Settings updated to ${updatedModuleSettings.bundle}")

                oldModule.updateSettings(updatedModuleSettings)
            } else {
                createModule(
                    context,
                    settings.moduleSettings.getOrDefault(factory.name),
                    factory
                )
            }

            if (newModule != null) {
                newModules[factory.name] = newModule
            }
        }
        _modules = newModules

        modulesSubject.onNext(_modules.values.toSet())
    }

    companion object {

        private fun createModule(
            context: TealiumContext,
            settings: ModuleSettings,
            factory: ModuleFactory
        ): Module? {
            return if (settings.enabled) {
                factory.create(context, settings)
            } else null
        }

        /**
         * Extracts the module settings for the named module; otherwise returns the default settings.
         */
        private fun getModuleSettings(
            moduleName: String,
            modulesSettings: Map<String, ModuleSettings>
        ): ModuleSettings {
            return modulesSettings[moduleName] ?: ModuleSettingsImpl()
        }

        /**
         * Extracts the module settings for the named module; otherwise returns the default settings.
         */
        private fun Map<String, ModuleSettings>.getOrDefault(name: String): ModuleSettings {
            return getModuleSettings(name, this)
        }
    }
}

