package com.tealium.core.api

import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumException
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.DataLayer
import com.tealium.core.api.modules.DeeplinkManager
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.modules.TimedEventsManager
import com.tealium.core.api.modules.TraceManager
import com.tealium.core.api.modules.VisitorService
import com.tealium.core.api.modules.consent.ConsentManager
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TrackResultListener
import com.tealium.core.internal.TealiumInstanceManager

interface Tealium {

    val key: String
    val trace: TraceManager
    val deeplink: DeeplinkManager
    val timedEvents: TimedEventsManager
    val dataLayer: DataLayer
    val consent: ConsentManager

    // Optionals
    val visitorService: VisitorService?

    fun <T: Module> createModuleProxy(clazz: Class<T>) : ModuleProxy<T>

    fun track(dispatch: Dispatch)
    fun track(dispatch: Dispatch, onComplete: TrackResultListener)

    fun flushEventQueue()

    /**
     * Resets the current visitor id to a new anonymous one.
     *
     * Note. the new anonymous id will be associated to any identity currently set.
     *
     * @param callback The block called with the new `visitorId`, if successful, or an error
     */
    fun resetVisitorId(callback: TealiumCallback<TealiumResult<String>>)

    /**
     * Removes all stored visitor identifiers as hashed identities, and generates a new
     * anonymous visitor id.
     *
     * * @param callback The block called with the new `visitorId`, if successful, or an error
     */
    fun clearStoredVisitorIds(callback: TealiumCallback<TealiumResult<String>>)

    /**
     * Shuts this instance of [Tealium] down and frees up all memory usage.
     *
     * After calling this method, no further input will be processed and future method calls to the
     * [Tealium] instance will fail.
     *
     * @see Tealium.shutdown
     */
    fun shutdown()

    /**
     * An [Exception] to signify that the [Tealium] instance has already been shutdown.
     */
    class TealiumShutdownException(message: String?): TealiumException(message)

    companion object: InstanceManager {
        private val instanceManager: InstanceManager = TealiumInstanceManager()

        @JvmStatic
        override fun create(
            config: TealiumConfig,
            onReady: TealiumCallback<TealiumResult<Tealium>>?
        ): Tealium =
            instanceManager.create(config, onReady)

        @JvmStatic
        override fun shutdown(instanceKey: String) =
            instanceManager.shutdown(instanceKey)

        @JvmStatic
        override fun get(instanceKey: String, callback: TealiumCallback<Tealium?>) =
            instanceManager.get(instanceKey, callback)
    }
}
