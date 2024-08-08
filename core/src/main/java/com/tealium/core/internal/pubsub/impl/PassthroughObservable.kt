package com.tealium.core.internal.pubsub.impl

import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Subject

/**
 * The [PassthroughObservable] simply wraps the underlying [Subject]. All emissions from the [subject]
 * are also emitted downstream.
 */
class PassthroughObservable<T>(
    private val subject: Subject<T>
) : Observable<T> by subject