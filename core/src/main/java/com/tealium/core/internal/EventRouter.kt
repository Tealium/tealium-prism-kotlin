package com.tealium.core.internal

import com.tealium.core.api.Messenger
import com.tealium.core.api.Subscribable
import com.tealium.core.api.listeners.Listener

interface EventRouter<T: Listener> : Subscribable<T> {
    fun <T: Listener> send(messenger: Messenger<T>)
}

