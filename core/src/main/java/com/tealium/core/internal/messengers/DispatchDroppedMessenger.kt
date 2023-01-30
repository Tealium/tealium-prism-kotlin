package com.tealium.core.internal.messengers

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Messenger
import com.tealium.core.api.listeners.DispatchDroppedListener
import kotlin.reflect.KClass

class DispatchDroppedMessenger(private val dispatch: Dispatch): Messenger<DispatchDroppedListener> {
    override val listenerClass: KClass<DispatchDroppedListener>
        get() = DispatchDroppedListener::class

    override fun deliver(listener: DispatchDroppedListener) {
        listener.onDispatchDropped(dispatch)
    }
}