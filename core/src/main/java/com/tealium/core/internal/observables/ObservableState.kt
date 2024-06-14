package com.tealium.core.internal.observables

import com.tealium.core.api.listeners.SubscribableState

/**
 * [ObservableState] is a read-only wrapper of a [StateSubject]. It can be used to provide
 * assurances that the [Subject] itself cannot be accidentally retrieved via casting, but that
 * accessors can still subscribe to the underlying [Subject].
 */
interface ObservableState<T>: Observable<T>, SubscribableState<T>
