package com.tealium.prism.core.internal.modules

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumCallback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleProxy
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
 * developer should wrap their access to [Tealium] through through a [ModuleProxy]
 *
 * @param clazz The class to retrieve from the Tealium modules list
 * @param moduleManager the moduleManager used to retrieve the module.
 */
class ModuleProxyImpl<T : Module>(
    private val clazz: Class<T>,
    private val moduleManager: Observable<ModuleManager?>,
    private val scheduler: Scheduler,
) : ModuleProxy<T> {

    private val asyncProxy: AsyncProxy<T> = AsyncProxyImpl(scheduler, observeEnabledModule())

    override fun getModule(callback: TealiumCallback<T?>) =
        asyncProxy.getProxiedObject(callback)

    override fun observeModule(): Subscribable<T?> =
        moduleManager.flatMapLatest { manager ->
            manager?.observeModule(clazz) ?: Observables.just(null)
        }.subscribeOn(scheduler)

    override fun <R> observeModule(
        transform: (T) -> Observable<R>
    ): Subscribable<R> =
        moduleManager.flatMapLatest { manager ->
            manager?.observeModule(clazz, transform) ?: Observables.empty()
        }.subscribeOn(scheduler)

    override fun <R> executeModuleTask(task: (T) -> R): Single<TealiumResult<R>> =
        asyncProxy.executeTask(task)

    override fun <R> executeAsyncModuleTask(task: (T, TealiumCallback<TealiumResult<R>>) -> Unit): Single<TealiumResult<R>>  =
        asyncProxy.executeAsyncTask(task)

    /**
     * Gets the [Module] as a [Observable] but also handles the checks for:
     *  - Tealium being shutdown
     *  - Module being disabled
     */
    private fun observeEnabledModule(): Observable<TealiumResult<T>> =
        moduleManager.flatMapLatest { manager ->
            if (manager == null)
                return@flatMapLatest Observables.just(
                    TealiumResult.failure(
                        Tealium.TealiumShutdownException(
                            "Tealium Instance has already been shutdown."
                        )
                    )
                )

            manager.observeModule(clazz).map { module ->
                if (module == null) {
                    TealiumResult.failure(
                        ModuleNotEnabledException("Module was not found.")
                    )
                } else {
                    TealiumResult.success(module)
                }
            }
        }
}