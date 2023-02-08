package com.tealium.core.internal.observables

import android.database.Observable
import com.tealium.core.api.Subscribable

open class SubscribableImpl<L>: Observable<L>(), Subscribable<L> {

    override fun subscribe(listener: L) = registerObserver(listener)

    override fun unsubscribe(listener: L) = unregisterObserver(listener)
}