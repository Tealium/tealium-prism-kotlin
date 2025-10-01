@file:JvmName("SingleUtils")

package com.tealium.prism.core.api.pubsub

import com.tealium.prism.core.api.misc.TealiumCallback
import com.tealium.prism.core.api.misc.TealiumResult

/**
 * A [Subscribable] implementation whereby only a single result is expected to be emitted to the subscriber.
 */
interface Single<T> : Subscribable<T>

/**
 *  A [Single] that completes with a [TealiumResult].
 *
 * With a [SingleResult] you can subscribe as any other type of [Single],
 * but you can also subscribe only for [onSuccess] or [onFailure] to receive the event
 * only in case the event is respectively either a success or a failure.
 *
 * So in case you want to handle both success and failure:
 * ```kotlin
 * single.subscribe { result ->
 *      try {
 *        val value = result.getOrThrow()
 *        // handle success
 *      } catch (ex: Exception) {
 *        // handle ex
 *      }
 * }
 * ```
 *
 * In case you want to handle only successes:
 * ```kotlin
 * single.onSuccess { value ->
 *   // Handle success
 * }
 * ```
 *
 * In case you want to handle only failures:
 * ```kotlin
 * single.onFailure { exception ->
 *   // Handle failure
 * }
 */
typealias SingleResult<T> = Single<TealiumResult<T>>

/**
 * Subscribes a handler for handling only the successful element of this [Single].
 *
 * No errors will be made available to the subscribed [handler]
 */
fun <T, R> Single<T>.onSuccess(handler: TealiumCallback<R>): Disposable where T : TealiumResult<R> {
    return subscribe { result ->
        try {
            val value = result.getOrThrow()
            handler.onComplete(value)
        } catch (ignore: Exception) {
        }
    }
}

/**
 * Subscribes a handler for handling only the failure element of this [Single].
 */
fun <T, R> Single<T>.onFailure(handler: TealiumCallback<Exception>): Disposable where T : TealiumResult<R> {
    return subscribe { result ->
        try {
            result.getOrThrow()
        } catch (ignore: Exception) {
            handler.onComplete(ignore)
        }
    }
}