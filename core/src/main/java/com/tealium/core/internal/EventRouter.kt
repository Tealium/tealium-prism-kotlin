package com.tealium.core.internal

import com.tealium.core.api.Messenger
import com.tealium.core.api.Subscribable
import com.tealium.core.api.listeners.Listener

interface EventRouter : Subscribable<Listener> {
    fun <T : Listener> send(messenger: Messenger<T>)
}

