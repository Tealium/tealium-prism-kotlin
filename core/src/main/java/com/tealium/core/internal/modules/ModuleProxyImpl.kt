package com.tealium.core.internal.modules

import com.tealium.core.api.Tealium
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.ModuleNotEnabledException
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Single
import com.tealium.core.api.pubsub.Subscribable

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

    override fun getModule(callback: TealiumCallback<T?>) {
        moduleManager.take(1)
            .subscribeOn(scheduler)
            .subscribe { moduleManager ->
                if (moduleManager == null) {
                    callback.onComplete(null)
                    return@subscribe
                }

                moduleManager.getModuleOfType(clazz, callback)
            }
    }

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
        executeModuleTaskInternal { module ->
            Observables.just(
                TealiumResult.success(task.invoke(module))
            )
        }

    override fun <R> executeModuleTask(task: (T, TealiumCallback<TealiumResult<R>>) -> Unit): Single<TealiumResult<R>> =
        executeModuleTaskInternal { module ->
            Observables.callback { observer ->
                try {
                    task.invoke(module) { result ->
                        observer.onNext(result)
                    }
                } catch (e: Exception) {
                    observer.onNext(TealiumResult.failure(e))
                }
            }
        }

    private fun <R> executeModuleTaskInternal(transform: (T) -> Observable<TealiumResult<R>>): Single<TealiumResult<R>> {
        val resultSubject = Observables.replaySubject<TealiumResult<R>>(1)

        observeEnabledModule().flatMap { result ->
            try {
                val module = result.getOrThrow()
                transform.invoke(module)
            } catch (e: Exception) {
                Observables.just(TealiumResult.failure(e))
            }
        }.asSingle(scheduler)
            .subscribe(resultSubject)

        // TODO - notify generic error handler?

        return resultSubject
            .asSingle(scheduler)
    }

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