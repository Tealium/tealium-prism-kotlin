package com.tealium.core.api

import com.tealium.core.api.barriers.Barrier
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumException
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.DataLayer
import com.tealium.core.api.modules.DeepLinkHandler
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.modules.Trace
import com.tealium.core.api.pubsub.Single
import com.tealium.core.api.pubsub.SingleResult
import com.tealium.core.api.tracking.TealiumDispatchType
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.internal.TealiumInstanceManager

interface Tealium {

    val key: String
    val trace: Trace
    val deeplink: DeepLinkHandler
    val dataLayer: DataLayer

    /**
     * Creates a [ModuleProxy] for the given module [clazz] to allow for an easy creation of Module Wrappers.
     *
     * This method should only be used when creating a [Module] wrapper. Use the already prebuilt wrappers for modules included in the SDK.
     *
     * @param clazz The [Module] type that the proxy needs to wrap.
     *
     * @return The [ModuleProxy] for the given module.
     */
    fun <T : Module> createModuleProxy(clazz: Class<T>): ModuleProxy<T>

    /**
     * Tracks an event with the specified name, and data. The event type will be [TealiumDispatchType.Event]
     *
     * @param name The name of the event to track.
     * @param data Additional data to include with the event (optional).
     *
     * @return: A [Single] onto which you can subscribe to receive the completion with the eventual error
     * or the [TrackResult] for this track request.
     */
    fun track(
        name: String,
        data: DataObject
    ): SingleResult<TrackResult>

    /**
     * Tracks an event with the specified name, type, and data.
     *
     * @param name The name of the event to track.
     * @param type The type of dispatch to use (default is .event).
     * @param data Additional data to include with the event (optional).
     *
     * @return A [Single] onto which you can subscribe to receive the completion with the eventual error
     * or the [TrackResult] for this track request.
     */
    fun track(
        name: String,
        type: TealiumDispatchType,
        data: DataObject
    ): SingleResult<TrackResult>

    /**
     * Flushes any queued events from the system when it is considered safe to do so by any [Barrier]s
     * that may be blocking.
     *
     * This method will not override those [Barrier] implementations whose [Barrier.isFlushable]
     * returns false. But when non-flushable barriers open, a flush will still occur.
     *
     * @return A [Single] onto which you can subscribe to receive the completion with the eventual error.
     *      The returned [Single], in case of success, completes when the flush request is accepted, not when all the events have been flushed.
     */
    fun flushEventQueue(): SingleResult<Unit>

    /**
     * Resets the current visitor id to a new anonymous one.
     *
     * Note. the new anonymous id will be associated to any identity currently set.
     *
     * @return A [SingleResult] which can be subscribed to to get the new `visitorId`, if successful, or an error
     */
    fun resetVisitorId(): SingleResult<String>

    /**
     * Removes all stored visitor identifiers as hashed identities, and generates a new
     * anonymous visitor id.
     *
     * @return A [SingleResult] which can be subscribed to to get the new `visitorId`, if successful, or an error
     */
    fun clearStoredVisitorIds(): SingleResult<String>

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
    class TealiumShutdownException(message: String?) : TealiumException(message)

    companion object : InstanceManager {
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
