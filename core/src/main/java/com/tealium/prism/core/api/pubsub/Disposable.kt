package com.tealium.prism.core.api.pubsub

/**
 * Defines a resource that can be disposed.
 */
interface Disposable {

    /**
     * Specifies whether or not this resource is currently disposed or not.
     */
    val isDisposed: Boolean

    /**
     * Disposes of this resource.
     */
    fun dispose()
}