package com.tealium.core.api

interface Collector {
    fun collect(): Map<String, Any>
}