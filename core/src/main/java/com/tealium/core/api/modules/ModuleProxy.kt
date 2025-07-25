package com.tealium.core.api.modules

import com.tealium.core.api.Tealium
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Single
import com.tealium.core.api.pubsub.Subscribable

/**
 * A [ModuleProxy] is to be used for proxying access to modules that are or were available
 * to access from the main [Tealium] implementation.
 *
 * Any external [Module] implementation that provides functionality expected to be used by a
 * developer should wrap their access to [Tealium] through a [ModuleProxy].
 */
interface ModuleProxy<T: Module> {

    /**
     * Retrieves the [Module], providing it in the [callback].
     *
     * @param callback: The block of code to receive the [Module] in, if present, or `null`
     */
    fun getModule(callback: TealiumCallback<T?>)

    /**
     * Observe an observable of the [Module] regardless of if the [Module] is currently enabled or not.
     *
     * @return A [Subscribable] for the inner [Observable].
     */
    fun observeModule() : Subscribable<T?>

    /**
     * Observe an observable of the [Module] regardless of if the [Module] is currently enabled or not.
     *
     * @param transform: The transformation that maps the [Module] to one of it's [Observable]s.
     * @return A [Subscribable] for the inner [Observable].
     */
    fun <R> observeModule(transform: (T) -> Observable<R>): Subscribable<R>

    /**
     * Eagerly executes a [task] for the Module, with the result returned as a [TealiumResult]
     *
     * @param task The task to execute for the [Module]
     *
     * @return [Single] containing either the result of the task, or the failing exception
     */
    fun <R> executeModuleTask(task: (T) -> R): Single<TealiumResult<R>>

    /**
     * Eagerly executes a [task] for the Module. The [task] should use the provided
     * callback to emit a result to the returned [Observable].
     *
     * @param task The task to execute for the [Module]
     *
     * @return [Single] containing either the result of the task, or the failing exception
     */
    fun <R> executeAsyncModuleTask(task: (T, TealiumCallback<TealiumResult<R>>) -> Unit): Single<TealiumResult<R>>
}