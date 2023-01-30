package com.tealium.core.internal.messengers

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Messenger
import com.tealium.core.api.listeners.DispatchQueuedListener
import kotlin.reflect.KClass

class DispatchQueuedMessenger(private val dispatch: Dispatch): Messenger<DispatchQueuedListener> {
    override val listenerClass: KClass<DispatchQueuedListener>
        get() = DispatchQueuedListener::class

    override fun deliver(listener: DispatchQueuedListener) {
        listener.onDispatchQueued(dispatch)
    }
}