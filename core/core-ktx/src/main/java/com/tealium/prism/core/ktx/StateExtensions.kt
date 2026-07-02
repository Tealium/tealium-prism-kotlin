package com.tealium.prism.core.ktx

import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.SubscribableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Converts this [SubscribableState] into a [StateFlow].
 *
 * The returned [StateFlow] is seeded with the current [SubscribableState.value] and updated as
 * subsequent values are emitted. It mirrors the state of the underlying [SubscribableState] for
 * the lifetime of the provided [scope].
 *
 * The subscription is disposed automatically when:
 * - The [SubscribableState] calls [Observer.onComplete], or
 * - The provided [scope] is canceled.
 *
 * Emissions are received on whatever thread the [SubscribableState] emits on. Use
 * [kotlinx.coroutines.flow.stateIn] with an explicit dispatcher if you need to confine
 * state updates to a specific thread.
 *
 * @param scope The [CoroutineScope] that governs the lifetime of the subscription. When this
 *   scope is canceled the upstream subscription is disposed. If the scope has no [Job] (e.g.
 *   [kotlinx.coroutines.GlobalScope]), the subscription will not be disposed on scope
 *   cancellation and will live until the upstream [SubscribableState] completes — this is
 *   intentional for application-lifetime use cases.
 * @return A [StateFlow] reflecting the current and future values of this [SubscribableState].
 */
fun <T> SubscribableState<T>.asStateFlow(scope: CoroutineScope): StateFlow<T> {
    val stateFlow = MutableStateFlow(value)

    val disposable = subscribe(object : Observer<T> {
        override fun onNext(value: T) {
            stateFlow.value = value
        }

        override fun onComplete() {
            // Upstream has terminated; no further updates will arrive.
            // The StateFlow retains its last known value.
        }
    })

    scope.coroutineContext[Job]?.invokeOnCompletion {
        disposable.dispose()
    }

    return stateFlow.asStateFlow()
}
