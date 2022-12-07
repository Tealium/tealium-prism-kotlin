package com.tealium.core.api

interface TimedEventsManager {
    fun start(name: String, data: Map<String, Any>?)
    fun stop(name: String)
    fun cancel(name: String)
    fun cancelAll()
}
