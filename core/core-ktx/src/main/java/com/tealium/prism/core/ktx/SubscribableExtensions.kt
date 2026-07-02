package com.tealium.prism.core.ktx

import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.Subscribable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.callbackFlow

/**
 * Converts this [Subscribable] into a cold [Flow].
 *
 * Each call to [Flow.collect] triggers a new, independent subscription to this [Subscribable].
 * Emissions from the [Subscribable] are forwarded to the [Flow] collector on whatever thread the
 * [Subscribable] emits on. Use [kotlinx.coroutines.flow.flowOn] downstream to switch the context
 * in which collection occurs.
 *
 * The subscription is disposed automatically when:
 * - The [Subscribable] calls [Observer.onComplete], which closes the flow normally, or
 * - The collector's coroutine is canceled, which disposes the upstream subscription.
 */
fun <T> Subscribable<T>.asFlow(): Flow<T> = callbackFlow {
    val disposable = subscribe(object : Observer<T> {
        override fun onNext(value: T) {
            trySend(value)
        }

        override fun onComplete() {
            close()
        }
    })

    awaitClose {
        disposable.dispose()
    }
}

/**
 * Collects all emissions from this [Subscribable] by converting it to a [Flow] and collecting
 * with the given [collector].
 *
 * This is a convenience equivalent to `subscribable.asFlow().collect(collector)`.
 *
 * Suspends until the [Subscribable] calls [Observer.onComplete] or the calling coroutine is
 * canceled. On cancellation the underlying subscription is disposed automatically.
 *
 * @param collector The [FlowCollector] to receive each emitted value.
 */
suspend fun <T> Subscribable<T>.collect(collector: FlowCollector<T>) =
    asFlow().collect(collector)
