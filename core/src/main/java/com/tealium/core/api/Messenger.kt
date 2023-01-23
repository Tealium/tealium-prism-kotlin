package com.tealium.core.api

import com.tealium.core.api.listeners.Listener
import java.util.*
import kotlin.reflect.KClass

abstract class Messenger<T : Listener>(listener: KClass<T>) {

    val listenerClass: KClass<T> = listener

    abstract fun deliver(listener: T)
}