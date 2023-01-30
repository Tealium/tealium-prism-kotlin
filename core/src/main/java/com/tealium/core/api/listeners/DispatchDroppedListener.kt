package com.tealium.core.api.listeners

import com.tealium.core.api.Dispatch

interface DispatchDroppedListener: Listener {
    fun onDispatchDropped(dispatch: Dispatch)
}
