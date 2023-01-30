package com.tealium.core.internal.messengers

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Messenger
import com.tealium.core.api.listeners.DispatchQueuedListener

class DispatchQueuedMessenger(private val dispatch: Dispatch): Messenger<DispatchQueuedListener>(
    DispatchQueuedListener::class) {
    override fun deliver(listener: DispatchQueuedListener) {
        listener.onDispatchQueued(dispatch)
    }
}