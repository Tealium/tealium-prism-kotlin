package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Single
import com.tealium.prism.core.api.pubsub.Subscribable

class SingleImpl<T> private constructor(
    private val subscribable: Subscribable<T>,
): Single<T>, Subscribable<T> by subscribable {
    constructor(observable: Observable<T>, scheduler: Scheduler): this(
        observable.take(1)
            .subscribeOn(scheduler)
    )
}