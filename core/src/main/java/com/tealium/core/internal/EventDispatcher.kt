package com.tealium.core.internal

import android.util.Log
import com.tealium.core.api.Messenger
import com.tealium.core.api.listeners.Listener
import java.util.*
import java.util.concurrent.ExecutorService
import kotlin.Exception

open class EventDispatcher<T : Listener>(
    private val backgroundService: ExecutorService? = null
) : EventRouter<T> {

    private val listenerMap = Collections.synchronizedMap(
        WeakHashMap<Listener, Boolean>()
    )

    override fun <T : Listener> send(messenger: Messenger<T>) {
        val task = {
            try {
                synchronized {
                    listenerMap.keys
                        .filterIsInstance(messenger.listenerClass.java)
                }.forEach { listener ->
                    try {
                        messenger.deliver(listener)
                    } catch (ex: Exception) {
                        Log.w("Events", "Exception delivery message to Listener $listener")
                    }
                }
            } catch (ex: Exception) {
                Log.w("Events", ex.message + "")
            }
        }

        backgroundService?.submit(task) ?: task()
    }

    override fun subscribe(listener: T) {
        synchronized {
            listenerMap[listener] = java.lang.Boolean.TRUE
        }
    }

    fun subscribeAll(listenerList: List<T>) {
        synchronized {
            listenerMap.putAll(listenerList.map { it to java.lang.Boolean.TRUE })
        }
    }

    override fun unsubscribe(listener: T) {
        synchronized {
            listenerMap.remove(listener)
        }
    }

    private fun <R> synchronized(block: () -> R): R {
        return synchronized(listenerMap) {
            block()
        }
    }
}