package com.tealium.core

import com.tealium.core.api.*
import com.tealium.core.api.listeners.TrackResultListener
import com.tealium.core.internal.*

interface Tealium {

    fun interface OnTealiumReady {
        fun onReady(tealium: Tealium, exception: Exception?)
    }

    val modules: ModuleManager

    val trace: TraceManager
    val deeplink: DeeplinkManager
    val timedEvents: TimedEventsManager
    val dataLayer: DataLayer
    val consent: ConsentManager

    // Optionals
    val visitorService: VisitorService?

    fun track(dispatch: Dispatch)
    fun track(dispatch: Dispatch, onComplete: TrackResultListener)

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