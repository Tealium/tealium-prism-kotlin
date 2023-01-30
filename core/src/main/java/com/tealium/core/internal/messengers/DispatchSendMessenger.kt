package com.tealium.core.internal.messengers

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Messenger
import com.tealium.core.api.listeners.DispatchSendListener
import kotlin.reflect.KClass

class DispatchSendMessenger(private val dispatches: List<Dispatch>): Messenger<DispatchSendListener> {
    override val listenerClass: KClass<DispatchSendListener>
        get() = DispatchSendListener::class

    override fun deliver(listener: DispatchSendListener) {
        listener.onDispatchSend(dispatches)
    }
}
