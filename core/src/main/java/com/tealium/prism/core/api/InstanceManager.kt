package com.tealium.prism.core.api

import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult

interface InstanceManager {

    /**
     * Creates a new [Tealium] instance based on the provided [config].
     *
     * Typical usage in an app would keep the returned instance alive for as long as the app is alive.
     * However, the returned [Tealium] should be shutdown by the user when no longer required by
     * calling either it's [Tealium.shutdown] method or the [Tealium.Companion.shutdown].
     *
     * @param config The required configuration options for this instance.
     *
     * @return The [Tealium] instance ready to accept input, although if the initialization fails,
     * any method calls made to this object will also fail.
     */
    fun create(config: TealiumConfig): Tealium = create(config, null)

    /**
     * Creates a new [Tealium] instance based on the provided [config].
     *
     * Typical usage in an app would keep the returned instance alive for as long as the app is alive.
     * However, the returned [Tealium] should be shutdown by the user when no longer required by
     * calling either it's [Tealium.shutdown] method or the [Tealium.Companion.shutdown].
     *
     * The [onReady] callback allows the caller to be notified once the instance is ready, or has
     * failed during initialization alongside the cause of the failure.
     *
     * @param config The required configuration options for this instance.
     *
     * @return The [Tealium] instance ready to accept input, although if the initialization fails,
     * any method calls made to this object will also fail.
     */
    fun create(
        config: TealiumConfig,
        onReady: Callback<TealiumResult<Tealium>>? = null
    ): Tealium

    /**
     * Shuts down the [Tealium] instance.
     *
     * After calling this method, no further input will be processed and future method calls to the
     * [tealium] instance will fail.
     *
     * @param tealium The [Tealium] instance to shutdown.
     */
    fun shutdown(tealium: Tealium) = shutdown(tealium.key)

    /**
     * Shuts down the [Tealium] instance identified by the [instanceKey]
     *
     * After calling this method, no further input will be processed and future method calls to the
     * [Tealium] instance will fail.
     *
     * @param instanceKey The key of the [Tealium] instance to shutdown.
     *
     * @see Tealium.key
     * @see TealiumConfig.key
     */
    fun shutdown(instanceKey: String)

    /**
     * Retrieves an existing [Tealium] instance, if one has already been created using its [TealiumConfig.key]
     *
     * @param config The config that was used to create the instance
     * @param callback The block to receive the [Tealium] instance on, if found.
     */
    fun get(config: TealiumConfig, callback: Callback<Tealium?>) = get(config.key, callback)

    /**
     * Retrieves an existing [Tealium] instance, if one has already been created, using the given [instanceKey]
     *
     * @param instanceKey The key that identifies the [Tealium] instance
     * @param callback The block to receive the [Tealium] instance on, if found.
     */
    fun get(instanceKey: String, callback: Callback<Tealium?>)
}