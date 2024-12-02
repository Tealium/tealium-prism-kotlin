package com.tealium.core.internal.modules

import com.tealium.core.api.Tealium
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observables
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
    private val scheduler: Scheduler
): ModuleProxy<T> {

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
            manager?.observeModule(clazz) ?: Observables.empty()
        }.subscribeOn(scheduler)

    override fun <R> observeModule(
        transform: (T) -> Observable<R>
    ): Subscribable<R> =
        moduleManager.flatMapLatest { manager ->
            manager?.observeModule(clazz, transform) ?: Observables.empty()
        }.subscribeOn(scheduler)
}
