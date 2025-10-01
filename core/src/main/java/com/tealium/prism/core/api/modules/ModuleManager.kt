package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.TealiumCallback
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.ObservableState

/**
 * The [ModuleManager] is responsible for managing [Module] implementations throughout the [Tealium]
 * instance lifecycle.
 * // TODO - complete
 */
interface ModuleManager {

    /**
     * Observable stream of all [Module] implementations in the system.
     */
    val modules: ObservableState<List<Module>>

    /**
     * Returns the first [Module] implementation that implements or extends the given [clazz].
     *
     * @param clazz The Class or Interface to match against
     * @return The first [Module] that matches the given [clazz]
     */
    fun <T: Module> getModuleOfType(clazz: Class<T>): T?

    /**
     * Returns the first [Module] implementation that implements or extends the given [clazz].
     *
     * @param clazz The Class or Interface to match against
     * @param callback The block of code to receive the [Module]
     */
    fun <T: Module> getModuleOfType(clazz: Class<T>, callback: TealiumCallback<T?>)

    /**
     * Observe an observable of the [Module] regardless of if the [Module] is currently enabled or not.
     *
     * @param clazz The [Class] to use to match the specific [Module]
     *
     * @return An [Observable] for monitoring status changes of a specific [Module].
     */
    fun <T : Module> observeModule(clazz: Class<T>): Observable<T?>

    /**
     * Observe an observable of the [Module] regardless of if the [Module] is currently enabled or not.
     *
     * @param clazz The [Class] to use to match the specific [Module]
     * @param transform: The transformation that maps the [Module] to one of it's [Observable]s.
     *
     * @return A [Observable] for the inner [Observable].
     */
    fun <T: Module, R> observeModule(
        clazz: Class<T>,
        transform: (T) -> Observable<R>
    ): Observable<R>
}
