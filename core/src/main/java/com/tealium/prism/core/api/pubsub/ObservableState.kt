package com.tealium.prism.core.api.pubsub

import com.tealium.prism.core.internal.pubsub.impl.ObservableStateImpl

/**
 * [ObservableState] is a read-only wrapper of a [StateSubject]. It can be used to provide
 * assurances that the [Subject] itself cannot be accidentally retrieved via casting, but that
 * accessors can still subscribe to the underlying [Subject].
 */
interface ObservableState<T>: Observable<T>, SubscribableState<T> {

    /**
     * Transforms the emissions and state of an existing [ObservableState] instance to a different type.
     * Typically useful for extracting sub-properties of an existing [ObservableState].
     *
     * @param transform The transformation block to convert [T] to a new type [R]
     * @return a new [ObservableState] emitting the new values of [R]
     */
    fun <R> mapState(transform: (T) -> R): ObservableState<R> {
        return ObservableStateImpl(this.map(transform)) {
            transform(value)
        }
    }
}
