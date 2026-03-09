package com.tealium.prism.core.api

import com.tealium.prism.core.api.barriers.Barrier
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.DataLayer
import com.tealium.prism.core.api.modules.DeepLinkHandler
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.modules.Trace
import com.tealium.prism.core.api.pubsub.Single
import com.tealium.prism.core.api.pubsub.SingleResult
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.internal.TealiumInstanceManager

/**
 * The main class for the Tealium SDK.
 *
 * This class provides the primary interface for interacting with the Tealium SDK.
 * It handles initialization, tracking events, managing visitor IDs, and accessing
 * various modules like data layer, deep linking, and tracing.
 */
interface Tealium {

    /**
     * The identifying [key] for this instance.
     */
    val key: String

    /**
     * Returns an object for managing traces. The [Trace] module is responsible for handling Tealium
     * trace registration.
     *
     * Joining a trace will add the trace id to each event for filtering server side. Users can leave
     * the trace when finished.
     */
    val trace: Trace

    /**
     * The [DeepLinkHandler] is responsible for tracking incoming deep links, managing attribution, and
     * handling trace parameters when present in the URL.
     *
     * DeepLink handling is automatically called unless explicitly disabled.
     */
    val deeplink: DeepLinkHandler

    /**
     * The [DataLayer] is available to store key-value data that should be present on every event
     * tracked through the Tealium SDK.
     *
     * There are a variety of getter/setter methods to store/retrieve common data types. All methods operate
     * on the Tealium thread.
     */
    val dataLayer: DataLayer

    /**
     * Executes the provided [callback] when [Tealium] is ready for use, from the [Tealium] internal thread.
     *
     * Usage of this method is incentivized in case you want to call multiple [Tealium] methods in a row.
     * Every one of those methods, if called from a different thread, will cause the execution to move into our
     * own internal queue, causing overhead.
     * Calling those methods from the [callback] of this method, instead, will skip all of those context switches
     * and perform the operations synchronously onto our thread.
     *
     * @param callback
     *  A closure that is called with the [Tealium] instance when it's ready.
     *  In case of an initialization error the completion won't be called at all.
     */
    fun onReady(callback: Callback<Tealium>)

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
     * Tracks an event with the specified name, and data. The event type will be [DispatchType.Event]
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
        type: DispatchType,
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
            onReady: Callback<TealiumResult<Tealium>>?
        ): Tealium =
            instanceManager.create(config, onReady)

        @JvmStatic
        override fun shutdown(instanceKey: String) =
            instanceManager.shutdown(instanceKey)

        @JvmStatic
        override fun get(instanceKey: String, callback: Callback<Tealium?>) =
            instanceManager.get(instanceKey, callback)
    }
}
