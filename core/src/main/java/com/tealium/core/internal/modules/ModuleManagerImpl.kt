package com.tealium.core.internal.modules

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.internal.settings.SdkSettings
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject

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
        val newModules = moduleFactories.mapNotNull { factory ->
            val module = oldModules[factory.id]
            val newSettings = settings.moduleSettings.getOrDefault(factory.id)
            if (module != null) {
                updateOrDisableModule(
                    factory.canBeDisabled(),
                    module,
                    newSettings,
                    context.logger
                )
            } else {
                createModule(
                    context,
                    newSettings,
                    factory
                )
            }
        }.associateBy { module ->
            module.id
        }
        _modules = newModules

        modulesSubject.onNext(_modules.values.toSet())
    }

    private fun updateOrDisableModule(
        canBeDisabled: Boolean,
        module: Module,
        settings: DataObject,
        logger: Logger
    ): Module? {
        if (!canBeDisabled || settings.enabled) {
            // update
            val updatedModule = module.updateSettings(settings)
            if (updatedModule != null) {
                logger.trace?.log(
                    module.id,
                    "Settings updated to $settings"
                )
                return updatedModule
            }
        }

        // shutdown
        logger.trace?.log(
            module.id,
            "Module failed to update settings. Module will be shut down."
        )
        module.onShutdown()
        return null
    }

    companion object {
        private val DataObject.enabled: Boolean
            get() = this.getBoolean("enabled") ?: true

        private fun createModule(
            context: TealiumContext,
            settings: DataObject,
            factory: ModuleFactory
        ): Module? {
            return if (!factory.canBeDisabled() || settings.enabled) {
                factory.create(context, settings)
            } else null
        }

        /**
         * Extracts the module settings for the named module; otherwise returns the default settings.
         */
        private fun getModuleSettings(
            moduleName: String,
            modulesSettings: Map<String, DataObject>
        ): DataObject {
            return modulesSettings[moduleName] ?: DataObject.EMPTY_OBJECT
        }

        /**
         * Extracts the module settings for the named module; otherwise returns the default settings.
         */
        private fun Map<String, DataObject>.getOrDefault(name: String): DataObject {
            return getModuleSettings(name, this)
        }
    }
}

