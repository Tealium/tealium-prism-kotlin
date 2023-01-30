package com.tealium.core.api

import com.tealium.core.api.listeners.ExternalListener
import com.tealium.core.api.listeners.Listener

interface MessengerService: Subscribable<Listener> {
    fun <T: ExternalListener> send(messenger: Messenger<T>)
}
