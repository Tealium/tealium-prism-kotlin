package com.tealium.core.api

interface TraceManager {
    fun killVisitorSession()
    fun join(id: String)
    fun leave()
}


