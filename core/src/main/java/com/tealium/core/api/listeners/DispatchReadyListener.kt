package com.tealium.core.api.listeners

import com.tealium.core.api.Dispatch

interface DispatchReadyListener : Listener {
    fun onDispatchReady(dispatch: Dispatch)
}