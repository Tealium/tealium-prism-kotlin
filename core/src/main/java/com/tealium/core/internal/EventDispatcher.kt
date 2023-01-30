package com.tealium.core.internal

import android.util.Log
import com.tealium.core.api.Messenger
import com.tealium.core.api.listeners.Listener
import java.lang.Exception
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService

class EventDispatcher(
    private val backgroundService: ExecutorService? = null
) : EventRouter {

    private val listeners = CopyOnWriteArraySet<Listener>()

    override fun <T : Listener> send(messenger: Messenger<T>) {
        val task = {
            try {
                listeners.filterIsInstance(messenger.listenerClass.java).forEach {
                    messenger.deliver(it)
                }
            } catch (ex: Exception) {
                Log.w("Events", ex.message + "")
            }
        }

        backgroundService?.submit(task) ?: task()
    }

    override fun subscribe(listener: Listener) {
        listeners.add(listener)
    }

    fun subscribeAll(listenerList: List<Listener>) {
        listeners.addAll(listenerList)
    }

    override fun unsubscribe(listener: Listener) {
        listeners.remove(listener)
    }
}