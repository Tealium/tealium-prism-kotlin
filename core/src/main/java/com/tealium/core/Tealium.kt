package com.tealium.core

import com.tealium.core.api.*
import com.tealium.core.api.listeners.Listener
import com.tealium.core.internal.*

interface Tealium {

    fun interface OnTealiumReady {
        fun onReady(tealium: Tealium)
    }

    val modules: ModuleManager
    val events: Subscribable<Listener>

    val trace: TraceManager
    val deeplink: DeeplinkManager
    val timedEvents: TimedEventsManager
    val dataLayer: DataLayer
    val consent: ConsentManager

    fun track(dispatch: Dispatch)
    fun flushEventQueue()

    companion object {
        private val instances = mutableMapOf<String, TealiumImpl>()

        @JvmStatic
        fun default(): Tealium? {
            return instances.values.firstOrNull()
        }

        @JvmStatic
        fun create(
            name: String,
            config: TealiumConfig,
            onReady: OnTealiumReady
        ): Tealium {
            return TealiumImpl(config, onReady).also {
                instances[name] = it
            }
        }

        @JvmStatic
        fun destroy(name: String) {
            instances[name]?.shutdown()
            instances.remove(name)
        }

        @JvmStatic
        operator fun get(name: String): Tealium? {
            return instances[name]
        }
    }
}