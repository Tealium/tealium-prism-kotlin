package com.tealium.core.api

interface Subscribable<T> {
    fun subscribe(listener: T)
    fun unsubscribe(listener: T)
}