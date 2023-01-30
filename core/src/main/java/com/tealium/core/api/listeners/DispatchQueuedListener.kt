package com.tealium.core.api.listeners

import com.tealium.core.api.Dispatch

interface DispatchQueuedListener: Listener {
    fun onDispatchQueued(dispatch: Dispatch)
}
