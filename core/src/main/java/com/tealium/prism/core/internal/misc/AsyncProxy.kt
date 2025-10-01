package com.tealium.prism.core.internal.misc

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumCallback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Single

/**
 * A proxy that allows access to the proxied object from a specific [Scheduler]
 *
 * All methods execute eagerly, even if the returned [Single] is not subscribed to.
 */
interface AsyncProxy<T> {

    /**
     * Attempts to fetch the proxied object, returning it in the given [callback], or `null` if
     * unavailable.
     *
     * @param callback The block of code to receive the proxied object.
     */
    fun getProxiedObject(callback: TealiumCallback<T?>)

    /**
     * Eagerly executes a [task] with the result returned as a [TealiumResult]
     *
     * @param task The task to execute
     *
     * @return [Single] containing either the result of the task, or the failing exception
     */
    fun <R> executeTask(task: (T) -> R): Single<TealiumResult<R>>

    /**
     * Eagerly executes a [task]. The [task] should use the provided callback to emit a result to
     * the returned [Observable].
     *
     * @param task The task to execute
     *
     * @return [Single] containing either the result of the task, or the failing exception
     */
    fun <R> executeAsyncTask(task: (T, TealiumCallback<TealiumResult<R>>) -> Unit): Single<TealiumResult<R>>
}

/**
 * Default implementation of [AsyncProxy].
 *
 * The provided [scheduler] should be the same one that the given [onObject] will emit from, as this
 * will be the scheduler used to subscribe to it.
 *
 * The provided [onObject] observable is an [Observable] that, upon subscription, emits the proxied
 * object as a successful [TealiumResult] if available, or as a failed [TealiumResult] if it's not available.
 *
 */
class AsyncProxyImpl<T>(
    private val scheduler: Scheduler,
    private val onObject: Observable<TealiumResult<T>>
): AsyncProxy<T> {

    override fun getProxiedObject(callback: TealiumCallback<T?>) {
        onObject.asSingle(scheduler)
            .subscribe { callback.onComplete(it.getOrNull()) }
    }

    override fun <R> executeTask(task: (T) -> R): Single<TealiumResult<R>> =
        executeAsyncTask { t, tealiumCallback ->
            val result = try {
                TealiumResult.success(task(t))
            } catch (e: Exception) {
                TealiumResult.failure(e)
            }

            tealiumCallback.onComplete(result)
        }

    override fun <R> executeAsyncTask(task: (T, TealiumCallback<TealiumResult<R>>) -> Unit): Single<TealiumResult<R>> {
        val replay = Observables.replaySubject<TealiumResult<R>>()

        onObject.callback { t, observer ->
            try {
                val value = t.getOrThrow()
                task(value) { result ->
                    observer.onNext(result)
                }
            } catch (e: Exception) {
                observer.onNext(TealiumResult.failure(e))
            }
        }.asSingle(scheduler)
            .subscribe(replay)

        return replay.asSingle(scheduler)
    }
}