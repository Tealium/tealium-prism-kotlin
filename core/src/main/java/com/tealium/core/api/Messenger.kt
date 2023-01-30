package com.tealium.core.api

import com.tealium.core.api.listeners.Listener
import kotlin.reflect.KClass

interface Messenger<T : Listener> {
    val listenerClass: KClass<T>
    fun deliver(listener: T)
}