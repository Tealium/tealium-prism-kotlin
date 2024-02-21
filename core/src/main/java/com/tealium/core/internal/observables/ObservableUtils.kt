package com.tealium.core.internal.observables

import com.tealium.core.api.listeners.Disposable

/**
 * Add the current disposable to a specific container for easier cleanup.
 */
fun Disposable.addTo(container: CompositeDisposable): Disposable = apply {
    container.add(this)
}