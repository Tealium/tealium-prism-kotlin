package com.tealium.core.api

/**
 * Defines an object whose updates can be subscribed to
 */
interface Subscribable<in T> {

    /**
     * Subscribe the given listener to updates
     *
     * @param listener - the object to receive handle notifications
     */
    fun subscribe(listener: T)

    /**
     * Subscribe a listener to updates
     *
     * @param listener - the object which should no longer receive notifications
     */
    fun unsubscribe(listener: T)
}