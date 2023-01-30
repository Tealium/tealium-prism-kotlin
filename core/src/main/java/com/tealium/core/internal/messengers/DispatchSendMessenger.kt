package com.tealium.core.internal.messengers

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Messenger
import com.tealium.core.api.listeners.DispatchSendListener

class DispatchSendMessenger(private val dispatches: List<Dispatch>): Messenger<DispatchSendListener>(DispatchSendListener::class) {
    override fun deliver(listener: DispatchSendListener) {
        listener.onDispatchSend(dispatches)
    }
}
