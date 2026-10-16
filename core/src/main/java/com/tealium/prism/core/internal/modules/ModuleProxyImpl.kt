package com.tealium.prism.core.internal.modules

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.pubsub.BiConsumer
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Single
import com.tealium.prism.core.api.pubsub.Subscribable
import com.tealium.prism.core.internal.misc.AsyncProxy
import com.tealium.prism.core.internal.misc.AsyncProxyImpl

/**
 * A [ModuleProxy] is to be used for proxying access to modules that are or were available
 * to access from the main [Tealium] implementation.
 *
 * Any external [Module] implementation that provides functionality expected to be used by an
 * developer should wrap their access to [Tealium] through a [ModuleProxy]
 *
 * @param clazz The class to retrieve from the Tealium modules list
 * @param moduleManagerResult The [ModuleManager] used to retrieve the module, or the exception
 * describing why the [Tealium] instance failed to initialize or was shut down.
 * @param scheduler The [Scheduler] to subscribe on.
 */
class ModuleProxyImpl<T : Module>(
    private val clazz: Class<T>,
    private val moduleManagerResult: Observable<TealiumResult<ModuleManager>>,
    private val scheduler: Scheduler,
) : ModuleProxy<T> {

    private val asyncProxy: AsyncProxy<T> = AsyncProxyImpl(scheduler, observeEnabledModule())

    override fun getModule(callback: Callback<T?>) =
        asyncProxy.getProxiedObject(callback)

    override fun observeModule(): Subscribable<T?> =
        moduleManagerResult.flatMapLatest { result ->
            result.getOrNull()?.observeModule(clazz) ?: Observables.empty()
        }.subscribeOn(scheduler)

    override fun <R> observeModule(
        transform: (T) -> Observable<R>
    ): Subscribable<R> =
        moduleManagerResult.flatMapLatest { result ->
            val manager = result.getOrNull()
                ?: return@flatMapLatest Observables.empty()

            manager.observeModule(clazz)
                .flatMapLatest { module -> if (module != null) transform(module) else Observables.empty() }
        }.subscribeOn(scheduler)

    override fun <R> observeModules(transform: (List<T>) -> Observable<R>): Subscribable<R> =
        moduleManagerResult.flatMapLatest { result ->
            val manager = result.getOrNull()
                ?: return@flatMapLatest Observables.empty()

            manager.observeModules(clazz)
                .flatMapLatest(transform)
        }.subscribeOn(scheduler)

    override fun <R> observeModuleResult(
        transform: (T) -> Observable<R>
    ): Subscribable<TealiumResult<R>> =
        moduleManagerResult.flatMapLatest { result ->
            val manager = result.getOrNull()
                ?: return@flatMapLatest Observables.just(failureOf<R>(result))

            // Nesting the module stream inside the manager stream ensures that switching
            // managers disposes the previous manager's module stream.
            manager.observeModule(clazz).flatMapLatest { module ->
                if (module == null) {
                    Observables.just(moduleNotFound())
                } else {
                    transform(module).map { TealiumResult.success(it) }
                }
            }
        }.subscribeOn(scheduler)

    override fun <R> executeModuleTask(task: (T) -> R): Single<TealiumResult<R>> =
        asyncProxy.executeTask(task)

    override fun <R> executeAsyncModuleTask(task: BiConsumer<T, Callback<TealiumResult<R>>>): Single<TealiumResult<R>> =
        asyncProxy.executeAsyncTask { module, callback -> task.accept(module, callback) }

    /**
     * Gets the [Module] as a [Observable] but also handles the checks for:
     *  - Tealium failing to initialize or being shutdown
     *  - Module being disabled
     */
    private fun observeEnabledModule(): Observable<TealiumResult<T>> =
        moduleManagerResult.flatMapLatest { result ->
            val manager = result.getOrNull()
                ?: return@flatMapLatest Observables.just(failureOf<T>(result))

            // Only the module state at the time a task starts matters; a module disabled while a
            // task is still running must not replace the task's result with ModuleNotEnabledException.
            manager.observeModule(clazz).take(1).map { module ->
                if (module == null) {
                    moduleNotFound()
                } else {
                    TealiumResult.success(module)
                }
            }
        }

    /**
     * The failure reported when the [Module] is disabled or missing.
     */
    private fun <R> moduleNotFound(): TealiumResult<R> =
        TealiumResult.failure(ModuleNotEnabledException("Module was not found."))

    /**
     * Forwards the exception of a failed [ModuleManager] result as is.
     */
    private fun <R> failureOf(result: TealiumResult<ModuleManager>): TealiumResult<R> =
        TealiumResult.failure(
            result.exceptionOrNull()
                ?: IllegalStateException("ModuleManager result was neither success nor failure.")
        )
}
