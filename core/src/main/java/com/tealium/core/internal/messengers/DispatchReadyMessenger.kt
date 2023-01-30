package com.tealium.core.internal.messengers

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Messenger
import com.tealium.core.api.listeners.DispatchReadyListener
import kotlin.reflect.KClass

class DispatchReadyMessenger(private val dispatch: Dispatch): Messenger<DispatchReadyListener> {
    override val listenerClass: KClass<DispatchReadyListener>
        get() = DispatchReadyListener::class

    override fun deliver(listener: DispatchReadyListener) {
        listener.onDispatchReady(dispatch)
    }
}
