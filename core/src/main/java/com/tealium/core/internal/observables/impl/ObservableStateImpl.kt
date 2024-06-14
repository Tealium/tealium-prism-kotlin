package com.tealium.core.internal.observables.impl

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.api.listeners.Observer
import com.tealium.core.api.listeners.SubscribableState
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.ObservableState
import com.tealium.core.internal.observables.StateSubject

/**
 * Default implementation of [ObservableState] that delegates all methods to the provided [StateSubject]
 */
class ObservableStateImpl<T>(
    private val subscribableState: SubscribableState<T>
) : ObservableState<T> {
    override fun subscribe(observer: Observer<T>): Disposable =
        subscribableState.subscribe(observer)

    override val value: T = subscribableState.value
}

class ObservableStateValue<T>(
    private val source: Observable<T>,
    private val valueSupplier: () -> T
) : ObservableState<T> {

    private var _value: T? = null

    override val value: T
        get() = _value ?: valueSupplier()

    override fun subscribe(observer: Observer<T>): Disposable {
        val parent = ObservableStateValueObserver(observer) { newValue ->
            _value = newValue
        }

        return source.subscribe(parent)
    }

    class ObservableStateValueObserver<T>(
        private val observer: Observer<T>,
        private val updateState: (T) -> Unit
    ) : Observer<T> {
        override fun onNext(value: T) {
            updateState(value)
            observer.onNext(value)
        }
    }
}