package com.tealium.core.api.modules

interface TraceManager {
    fun killVisitorSession()
    fun join(id: String)
    fun leave()
}


