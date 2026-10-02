package com.tealium.prism.core.internal.modules

import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Consumer
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.ReplaySubject
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.core.internal.settings.SdkSettings

/**
 * Compares two module lists by identity and order.
 *
 * Comparison is by identity rather than by [Module.id] or `equals`: a module reconfigured in place
 * is the same instance and must not be re-emitted, and a module overriding `equals` must not hide
 * a different instance. It is order sensitive because the module order is significant.
 */
internal fun sameModules(a: List<Module>, b: List<Module>): Boolean =
    a.size == b.size && a.indices.all { index -> a[index] === b[index] }

class ModuleManagerImpl(
    private val scheduler: Scheduler,
    private val _modules: StateSubject<List<Module>> = Observables.stateSubject(listOf())
) : InternalModuleManager {

    private val moduleFactories: MutableMap<String, ModuleFactory> = linkedMapOf()

    /**
     * The modules of the last completed settings pass, seeded with an empty list.
     *
     * A synchronous read cannot tell "no such module is enabled" from "the modules have not been
     * created yet", so anything making a permanent decision out of a missing module has to go
     * through [observeModule], [observeModules] or [getModuleOfType] instead, which withhold that
     * premature answer. Reading this directly is only safe once the modules have been created and
     * while no pass is running - as it is at collect or dispatch time, which the `Tracker` only
     * reaches with the modules already enabled.
     */
    internal val modules: ObservableState<List<Module>>
        get() = _modules.asObservableState()

    /**
     * Emits once the modules of a settings pass have all been created, and is cleared while a pass
     * is running, so a subscriber arriving mid-pass waits for it to complete instead of being
     * replayed the answer of the previous one.
     *
     * [shutdown] emits it again on an empty modules list: after a shutdown no module is ever going
     * to arrive, so waiting for one is the one answer that would never come.
     */
    private val onReady: ReplaySubject<Unit> = Observables.replaySubject()

    init {
        // Modules provided upfront are ready without waiting for a settings pass.
        if (_modules.value.isNotEmpty()) {
            onReady.onNext(Unit)
        }
    }

    override fun addModuleFactory(moduleFactory: ModuleFactory): Boolean {
        if (moduleFactories.contains(moduleFactory.moduleType)) {
            return false
        }

        moduleFactories[moduleFactory.moduleType] = moduleFactory
        return true
    }

    /**
     * The gate only holds back a subscription until the first pass has finished: [Observable.take]
     * completes on the first emission, so the later ones - end of each pass, and shutdown - do not
     * open a second subscription to [_modules] and replay the same list twice.
     * A subscriber that is already through is protected by [updateModuleSettings] instead, which
     * publishes the new list exactly once per pass, after every module of that pass has been
     * created - so there is no intermediate list for it to be handed while a pass is running.
     */
    private fun observeReadyModules(): Observable<List<Module>> =
        onReady.take(1)
            .flatMap { _modules.distinct(::sameModules) }

    override fun <T : Module> observeModule(clazz: Class<T>): Observable<T?> =
        observeReadyModules()
            .map { modules -> modules.firstOrNull(clazz::isInstance)?.let(clazz::cast) }
            .distinct { old, new -> old === new }
            .subscribeOn(scheduler)

    override fun <T : Module> observeModules(clazz: Class<T>): Observable<List<T>> =
        observeReadyModules()
            .map { modules -> modules.filterIsInstance(clazz) }
            .distinct(::sameModules)
            .subscribeOn(scheduler)

    override fun <T : Module> getModuleOfType(clazz: Class<T>, callback: Callback<T?>): Disposable =
        observeModule(clazz)
            .subscribeOnce(Consumer { module -> callback.onComplete(module) })

    override fun shutdown() {
        // close the gate first, or a lookup started by a module being shut down would be handed
        // the modules that are on their way out
        onReady.clear()
        for (module in _modules.value) {
            module.onShutdown()
        }
        moduleFactories.clear()
        _modules.onNext(emptyList())
        // only once that empty list is in place, or a waiter would be handed the shut down modules
        onReady.onNext(Unit)
    }

    override fun updateModuleSettings(context: TealiumContext, settings: SdkSettings) {
        // close the gate for the duration of the pass: the cached list is stale from here on, and
        // modules created below may subscribe from their own constructor
        onReady.clear()

        val previous = _modules.value
        val updated = updateModules(context, settings)

        /*
         * Any module of the previous pass that did not make it into this one is shut down here,
         * whether it was disabled, returned `null` from `updateConfiguration`, or was simply removed
         * from the settings entirely. Matching by identity also shuts down an instance that
         * `updateConfiguration` swapped for another, which the contract does not allow. This runs
         * before publishing so no consumer ever observes a live list holding a module that is dead.
         */
        previous.filter { old -> updated.none { it === old } }
            .forEach { module -> shutdownModule(module, context.logger) }

        _modules.onNext(updated)
        onReady.onNext(Unit)
    }

    private fun updateModules(context: TealiumContext, settings: SdkSettings): List<Module> {
        val oldModules = _modules.value.associateBy(Module::id)
        val allModuleSettings = prepareModuleSettings(settings)

        val createdModuleTypes = mutableSetOf<String>()
        return allModuleSettings.mapNotNull { newSettings ->
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
    }

    /**
     * Returns a list of all [ModuleSettings] from the given [SdkSettings]
     *
     * The returned list is sorted by [ModuleSettings.order], and deduplicated by [ModuleSettings.moduleId].
     */
    private fun prepareModuleSettings(settings: SdkSettings): List<ModuleSettings> {
        return settings.modules.values.sortedBy { it.order }
            .distinctBy { it.moduleId }
    }

    /**
     * Returns the [Module] to keep for this pass, or `null` if it has to be dropped from it. The
     * shutdown of a dropped module is left to [updateModuleSettings], which shuts down every module
     * that did not make it into the new list.
     */
    private fun updateOrDisableModule(
        canBeDisabled: Boolean,
        module: Module,
        settings: ModuleSettings,
        logger: Logger
    ): Module? {
        if (canBeDisabled && !settings.enabled) {
            logger.debug(module.id, "Module has been marked as disabled. Module will be shut down.")
            return null
        }

        val updatedModule = module.updateConfiguration(settings.configuration)
        if (updatedModule == null) {
            logger.trace(
                module.id, "Module failed to update configuration. Module will be shut down."
            )
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
