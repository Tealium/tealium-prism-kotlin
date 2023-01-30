package com.tealium.core.internal.messengers

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Messenger
import com.tealium.core.api.listeners.DispatchReadyListener

class DispatchReadyMessenger(private val dispatch: Dispatch): Messenger<DispatchReadyListener>(DispatchReadyListener::class) {
    override fun deliver(listener: DispatchReadyListener) {
        listener.onDispatchReady(dispatch)
    }
}
