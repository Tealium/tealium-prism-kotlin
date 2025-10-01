package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Subject

/**
 * The [PassthroughObservable] simply wraps the underlying [Subject]. All emissions from the [subject]
 * are also emitted downstream.
 */
class PassthroughObservable<T>(
    private val subject: Subject<T>
) : Observable<T> by subject