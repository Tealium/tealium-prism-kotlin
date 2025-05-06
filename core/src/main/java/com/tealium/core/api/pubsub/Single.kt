@file:JvmName("SingleUtils")

package com.tealium.core.api.pubsub

import com.tealium.core.api.misc.TealiumResult
import java.util.function.Consumer

/**
 * A [Subscribable] implementation whereby only a single result is expected to be emitted to the subscriber.
 */
interface Single<T> : Subscribable<T>

/**
 * Subscribes a handler for handling only the successful element of this [Single].
 *
 * No errors will be made available to the subscribed [handler]
 */
fun <T, R> Single<T>.onSuccess(handler: Consumer<R>): Disposable where T : TealiumResult<R> {
    return subscribe { result ->
        try {
            val value = result.getOrThrow()
            handler.accept(value)
        } catch (ignore: Exception) {
        }
    }
}

/**
 * Subscribes a handler for handling only the failure element of this [Single].
 */
fun <T, R> Single<T>.onFailure(handler: Consumer<Exception>): Disposable where T : TealiumResult<R> {
    return subscribe { result ->
        try {
            result.getOrThrow()
        } catch (ignore: Exception) {
            handler.accept(ignore)
        }
    }
}