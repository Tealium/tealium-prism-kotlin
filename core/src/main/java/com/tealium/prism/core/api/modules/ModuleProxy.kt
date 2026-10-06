package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Single
import com.tealium.prism.core.api.pubsub.Subscribable

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
    fun getModule(callback: Callback<T?>)

    /**
     * Observe an [Observable] of the [Module] regardless of if the [Module] is currently enabled or not.
     *
     * The stream completes once the [Tealium] instance fails to initialize or is shut down.
     *
     * @return A [Subscribable] for the inner [Observable].
     */
    fun observeModule() : Subscribable<T?>

    /**
     * Observes an [Observable] derived from the [Module], re-deriving it as the module's
     * availability changes.
     *
     * Use this for event streams: it emits only while the [Module] is enabled, stays silent while it
     * is disabled or missing, and completes once the [Tealium] instance fails to initialize or is
     * shut down. For state streams, where subscribers must know that the [Module] is unavailable,
     * use [observeModuleResult] instead.
     *
     * Values the [Module] emits while the [Tealium] instance is shutting down are not delivered: the
     * completion is the last event a subscriber receives.
     *
     * @param transform: The transformation that maps the [Module] to one of it's [Observable]s.
     * @return A [Subscribable] for the inner [Observable].
     */
    fun <R> observeModule(transform: (T) -> Observable<R>): Subscribable<R>

    /**
     * Observe observables from all [Module] instances of type [T].
     *
     * The [transform] is provided with a list of all currently available [Module]s, and should
     * return an [Observable] that emits values based on the provided list of [Module]s.
     *
     * The stream completes once the [Tealium] instance fails to initialize or is shut down.
     *
     * @param transform: The transformation that maps the list of [Module]s to an [Observable].
     * @return A [Subscribable] for the inner [Observable].
     */
    fun <R> observeModules(transform: (List<T>) -> Observable<R>): Subscribable<R>

    /**
     * Observes an [Observable] derived from the [Module], re-deriving it as the module's
     * availability changes.
     *
     * Use this for state streams, where subscribers must know when the [Module] is unavailable. For
     * event streams, use [observeModule] instead.
     *
     * The stream emits nothing until the [ModuleManager] has finished loading its modules. Once it
     * has, it emits [TealiumResult.success] values produced by [transform] while the module is
     * enabled, and a [TealiumResult.failure] carrying [ModuleNotEnabledException] while the module
     * is disabled or missing, switching back to success values if the module is re-enabled.
     *
     * If the [Tealium] instance failed to initialize, or is later shut down, the stream emits a
     * single failure carrying that exception ([Tealium.TealiumShutdownException] on shutdown) and
     * then completes.
     *
     * Values the [Module] emits while the [Tealium] instance is shutting down are not delivered: the
     * terminal failure is the last value a subscriber receives.
     *
     * @param transform The transformation that maps the [Module] to one of its [Observable]s.
     * @return A [Subscribable] of [TealiumResult]s wrapping the values of the inner [Observable].
     */
    fun <R> observeModuleResult(transform: (T) -> Observable<R>): Subscribable<TealiumResult<R>>

    /**
     * Eagerly executes a [task] for the Module, with the result returned as a [TealiumResult]
     *
     * Fails with [ModuleNotEnabledException] if the [Module] is not enabled, or with the
     * initialization exception or [Tealium.TealiumShutdownException] if the [Tealium] instance
     * failed to initialize or was shut down.
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
     * Fails with [ModuleNotEnabledException] if the [Module] is not enabled, or with the
     * initialization exception or [Tealium.TealiumShutdownException] if the [Tealium] instance
     * failed to initialize or was shut down.
     *
     * Once the [task] has been dispatched, its result is whatever the [Module] reports through the
     * callback, even if the [Module] is disabled in the meantime. Modules are therefore expected to
     * complete every pending callback when they are shut down, ideally with
     * [ModuleShutdownException]. Only a [Tealium] shutdown overrides this: it fails a task that is
     * still running with [Tealium.TealiumShutdownException].
     *
     * @param task The task to execute for the [Module]
     *
     * @return [Single] containing either the result of the task, or the failing exception
     */
    fun <R> executeAsyncModuleTask(task: (T, Callback<TealiumResult<R>>) -> Unit): Single<TealiumResult<R>>
}