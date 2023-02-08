package com.tealium.core.internal.observables

import com.tealium.core.api.data.ObservableProperty

open class ObservablePropertyImpl<T, L>(
    initialValue: T
) : SubscribableImpl<L>(), ObservableProperty<T, L> {
    // TODO - consider writing underlying Observable<L> implementation ourselves

    @Volatile
    protected var value = initialValue

    override fun get(): T {
        return value
    }
}