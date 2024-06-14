package com.tealium.core

import com.tealium.core.api.*
import com.tealium.core.api.consent.ConsentManager
import com.tealium.core.api.listeners.TealiumCallback
import com.tealium.core.api.listeners.TrackResultListener
import com.tealium.core.internal.*
import java.util.concurrent.Executors

interface Tealium {

    val trace: TraceManager
    val deeplink: DeeplinkManager
    val timedEvents: TimedEventsManager
    val dataLayer: DataLayer
    val consent: ConsentManager

    // Optionals
    val visitorService: VisitorService?

    /**
     * Returns the first [Module] implementation that implements or extends the given [Class].
     *
     * @param clazz The Class or Interface to match against
     * @param callback The block of code to receive the [Module]
     */
    fun <T> getModule(clazz: Class<T>, callback: TealiumCallback<T?>)

    fun track(dispatch: Dispatch)
    fun track(dispatch: Dispatch, onComplete: TrackResultListener)

    fun flushEventQueue()

    companion object {
        private val instances = mutableMapOf<String, TealiumProxy>()
        private val tealiumExecutor by lazy {
            Executors.newSingleThreadScheduledExecutor()
        }
        private val ioExecutor by lazy {
            Executors.newCachedThreadPool()
        }

        @JvmStatic
        fun default(): Tealium? {
            return instances.values.firstOrNull()
        }

        /**
         * Creates a new [Tealium] instance
         */
        @JvmStatic
        fun create(
            name: String,
            config: TealiumConfig,
        ): Tealium {
            return create(name, config, null)
        }

        /**
         * Creates a new [Tealium] instance
         */
        @JvmStatic
        fun create(
            name: String,
            config: TealiumConfig,
            onReady: TealiumCallback<TealiumResult<Tealium>>?
        ): Tealium {
            return TealiumProxy(
                config,
                onReady,
                TealiumScheduler(tealiumExecutor),
                {
                    IoScheduler(ioExecutor, tealiumExecutor)
                }
            ).also {
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
