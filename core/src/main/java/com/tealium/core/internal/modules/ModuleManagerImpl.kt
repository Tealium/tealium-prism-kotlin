package com.tealium.core.internal.modules

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.logger.logIfTraceEnabled
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.ModuleInfo
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.internal.settings.ModuleSettings
import com.tealium.core.internal.settings.SdkSettings

class ModuleManagerImpl(
    private val scheduler: Scheduler,
    private val _modules: StateSubject<List<Module>> = Observables.stateSubject(listOf())
) : InternalModuleManager {

    private val moduleFactories: MutableMap<String, ModuleFactory> = linkedMapOf()

    override val modulesInfo: List<ModuleInfo>
        get() = _modules.value.map(::getModuleInfo)

    override val modules: ObservableState<List<Module>>
        get() = _modules.asObservableState()

    override fun addModuleFactory(moduleFactory: ModuleFactory): Boolean {
        if (moduleFactories.contains(moduleFactory.id)) {
            return false
        }

        moduleFactories[moduleFactory.id] = moduleFactory
        return true
    }

    override fun <T : Module> getModulesOfType(clazz: Class<T>): List<T> {
        return _modules.value
            .filterIsInstance(clazz)
    }

    override fun <T : Module> getModuleOfType(clazz: Class<T>): T? {
        for (module in _modules.value) {
            if (clazz.isInstance(module)) {
                return clazz.cast(module)
            }
        }

        return null
    }

    override fun <T : Module> getModuleOfType(clazz: Class<T>, callback: TealiumCallback<T?>) {
        scheduler.execute {
            callback.onComplete(getModuleOfType(clazz))
        }
    }

    override fun <T : Module> observeModule(clazz: Class<T>): Observable<T?> =
        _modules.map { modules ->
            modules.filterIsInstance(clazz).firstOrNull()
        }.distinct()
            .subscribeOn(scheduler)

    override fun <T : Module, R> observeModule(
        clazz: Class<T>,
        transform: (T) -> Observable<R>
    ): Observable<R> = observeModule(clazz)
        .flatMapLatest { module ->
            if (module != null) {
                transform(module)
            } else {
                Observables.empty()
            }
        }.subscribeOn(scheduler)

    override fun shutdown() {
        for (module in _modules.value) {
            module.onShutdown()
        }
        moduleFactories.clear()
        _modules.onNext(emptyList())
    }

    override fun updateModuleSettings(context: TealiumContext, settings: SdkSettings) {
        // iterate all factories to preserve insertion order
        val oldModules = _modules.value.associateBy(Module::id)
        val newModules = moduleFactories.mapNotNull { (id, factory) ->
            val module = oldModules[id]
            val newSettings = settings.modules.getOrDefault(id)
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
        }.toList()

        _modules.onNext(newModules)
    }

    private fun updateOrDisableModule(
        canBeDisabled: Boolean,
        module: Module,
        settings: ModuleSettings,
        logger: Logger
    ): Module? {
        if (!canBeDisabled || settings.enabled) {
            // update
            val updatedModule = module.updateSettings(settings.configuration)
            if (updatedModule != null) {
                logger.logIfTraceEnabled(module.id) {
                    "Settings updated to ${settings.configuration}"
                }
                return updatedModule
            }
        }

        // shutdown
        logger.trace(
            module.id,
            "Module failed to update settings. Module will be shut down."
        )
        module.onShutdown()
        return null
    }

    companion object {
        private fun getModuleInfo(module: Module) : ModuleInfo =
            ModuleInfo(module.id, module.version)

        private fun createModule(
            context: TealiumContext,
            settings: ModuleSettings,
            factory: ModuleFactory
        ): Module? {
            return if (!factory.canBeDisabled() || settings.enabled) {
                factory.create(context, settings.configuration)
            } else null
        }

        /**
         * Extracts the module settings for the named module; otherwise returns the default settings.
         */
        private fun getModuleSettings(
            moduleName: String,
            modulesSettings: Map<String, ModuleSettings>
        ): ModuleSettings {
            return modulesSettings[moduleName] ?: ModuleSettings()
        }

        /**
         * Extracts the module settings for the named module; otherwise returns the default settings.
         */
        private fun Map<String, ModuleSettings>.getOrDefault(name: String): ModuleSettings {
            return getModuleSettings(name, this)
        }
    }
}

