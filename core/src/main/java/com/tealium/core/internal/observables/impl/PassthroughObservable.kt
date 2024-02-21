package com.tealium.core.internal.observables.impl

import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.Subject

/**
 * The [PassthroughObservable] simply wraps the underlying [Subject]. All emissions from the [subject]
 * are also emitted downstream.
 */
class PassthroughObservable<T>(
    private val subject: Subject<T>
) : Observable<T> by subject