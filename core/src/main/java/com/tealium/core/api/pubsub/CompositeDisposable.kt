package com.tealium.core.api.pubsub

/**
 * Defines a [Disposable] implementation that can dispose of multiple other [Disposable]
 * implementations in one go.
 */
interface CompositeDisposable : Disposable {
    /**
     * Adds the given [disposable] to be disposed of later.
     */
    fun add(disposable: Disposable)

    /**
     * Removes the given [disposable] if it present.
     */
    fun remove(disposable: Disposable)
}