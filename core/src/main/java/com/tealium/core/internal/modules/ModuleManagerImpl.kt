package com.tealium.core.internal.modules

import com.tealium.core.api.logger.Logger
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
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

    override val modules: ObservableState<List<Module>>
        get() = _modules.asObservableState()

    override fun addModuleFactory(moduleFactory: ModuleFactory): Boolean {
        if (moduleFactories.contains(moduleFactory.moduleType)) {
            return false
        }

        moduleFactories[moduleFactory.moduleType] = moduleFactory
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
        val oldModules = _modules.value.associateBy(Module::id)

        val allModuleSettings = prepareModuleSettings(settings)

        val createdModuleTypes = mutableSetOf<String>()
        val newModules = allModuleSettings.mapNotNull { newSettings ->
            val factory = moduleFactories[newSettings.moduleType]

            if (factory == null) {
                context.logger.warn(
                    newSettings.moduleId,
                    "Attempted to create module but factory implementation (${newSettings.moduleType}) not found"
                )
                return@mapNotNull null
            }

            val firstInstanceForType = createdModuleTypes.add(factory.moduleType)
            if (!firstInstanceForType && !factory.allowsMultipleInstances) {
                context.logger.warn(
                    newSettings.moduleId,
                    "Attempted to create a second module instance for type (${newSettings.moduleType}); but instance already exists."
                )
                return@mapNotNull null
            }

            val moduleId = if (factory.allowsMultipleInstances) newSettings.moduleId else factory.moduleType
            val module = oldModules[moduleId]
            if (module != null) {
                updateOrDisableModule(factory.canBeDisabled(), module, newSettings, context.logger)
            } else {
                createModule(moduleId, context, newSettings, factory)
            }
        }

        _modules.onNext(newModules)
    }

    /**
     * Returns a list of all [ModuleSettings] from the given [SdkSettings], joined with additional
     * default [ModuleSettings] for any [ModuleFactory] that was added but does not have any settings
     * available.
     *
     * The returned list is sorted by [ModuleSettings.order], and deduplicated by [ModuleSettings.moduleId].
     */
    private fun prepareModuleSettings(settings: SdkSettings): List<ModuleSettings> {
        val sortedModuleSettings = settings.modules.values.sortedBy { it.order }

        val missingModuleSettings = moduleFactories.filterValues { factory ->
            settings.modules.values.find { it.moduleType == factory.moduleType } == null
        }.map { ModuleSettings(it.value.moduleType, order = Int.MAX_VALUE) }

        val deduplicatedModuleSettings = (sortedModuleSettings + missingModuleSettings)
            .distinctBy { it.moduleId }

        return deduplicatedModuleSettings
    }

    private fun updateOrDisableModule(
        canBeDisabled: Boolean,
        module: Module,
        settings: ModuleSettings,
        logger: Logger
    ): Module? {
        if (canBeDisabled && !settings.enabled) {
            logger.debug(module.id, "Module has been marked as disabled. Module will be shut down.")
            shutdownModule(module, logger)
            return null
        }

        val updatedModule = module.updateConfiguration(settings.configuration)
        if (updatedModule == null) {
            logger.trace(
                module.id, "Module failed to update configuration. Module will be shut down."
            )
            shutdownModule(module, logger)
            return null
        }

        logger.trace(module.id, "Configuration updated to ${settings.configuration}")
        return updatedModule
    }

    private fun shutdownModule(module: Module, logger: Logger) {
        module.onShutdown()
        logger.trace(module.id, "Module has been shut down.")
    }

    private fun createModule(
        moduleId: String,
        context: TealiumContext,
        settings: ModuleSettings,
        factory: ModuleFactory
    ): Module? {
        return if (!factory.canBeDisabled() || settings.enabled) {
            factory.create(moduleId, context, settings.configuration)
        } else {
            context.logger.debug(settings.moduleId, "Module failed to initialize.")
            null
        }
    }
}

