package com.tealium.core.internal.modules

import com.tealium.core.Tealium
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleManager
import com.tealium.core.api.listeners.TealiumCallback
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.Observables
import java.lang.ref.SoftReference

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
class ModuleProxy<T : Module>(
    private val clazz: Class<T>,
    private val moduleManager: ModuleManager
) {

    /**
     * Retrieves the proxied [Module] as an [Observable] to support [Module] implementations that
     * specify events that can be subscribed to.
     */
    fun getModule(): Observable<T> {
        return Observables.callback { observer ->
            moduleManager.getModuleOfType(clazz) { module ->
                if (module != null) {
                    observer.onNext(module)
                }
            }
        }
    }

    /**
     * Retrieves the [Module] for the given [clazz], providing it in the [callback]
     *
     * @param callback the block of code to receive the [Module] in
     */
    fun getModule(callback: TealiumCallback<T?>) {
        moduleManager.getModuleOfType(clazz, callback)
    }
}
