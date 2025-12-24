package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subscribable
import com.tealium.prism.core.api.pubsub.SubscribableState

/**
 * Default implementation of [ObservableState].
 *
 * @param source The [Subscribable] source used to emit values downstream
 * @param valueSupplier The block to provide an up-to-date value for [value]
 */
class ObservableStateImpl<T>(
    private val source: Subscribable<T>,
    private val valueSupplier: () -> T
) : ObservableState<T> {

    /**
     * Convenience constructor to delegate all [ObservableState] methods, [subscribe] and [value]
     * to the provided [source]. Typically this is useful for making [StateSubject]s read-only.
     *
     * @param source The [SubscribableState] to delegate all methods to.
     */
    constructor(source: SubscribableState<T>): this(source, source::value)

    override val value: T
        get() = valueSupplier()

    override fun subscribe(observer: Observer<T>): Disposable {
        return source.subscribe(observer)
    }
}