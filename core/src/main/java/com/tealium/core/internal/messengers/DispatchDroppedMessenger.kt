package com.tealium.core.internal.messengers

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Messenger
import com.tealium.core.api.listeners.DispatchDroppedListener

class DispatchDroppedMessenger(private val dispatch: Dispatch): Messenger<DispatchDroppedListener>(
    DispatchDroppedListener::class) {
    override fun deliver(listener: DispatchDroppedListener) {
        listener.onDispatchDropped(dispatch)
    }
}