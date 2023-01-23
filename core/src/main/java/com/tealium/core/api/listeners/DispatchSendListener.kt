package com.tealium.core.api.listeners

import com.tealium.core.api.Dispatch

interface DispatchSendListener : Listener {
    fun onDispatchSend(dispatches: List<Dispatch>)
}