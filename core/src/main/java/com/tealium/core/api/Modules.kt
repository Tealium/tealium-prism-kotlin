package com.tealium.core.api

import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.settings.CollectDispatcherSettingsBuilder
import com.tealium.core.api.settings.DeepLinkSettingsBuilder
import com.tealium.core.api.settings.VisitorServiceSettingsBuilder
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.internal.modules.VisitorServiceImpl
import com.tealium.core.internal.modules.collect.CollectDispatcher
import com.tealium.core.internal.modules.deeplink.DeepLinkHandlerModule

/**
 * An object used for configuring available modules and retrieving them as a [ModuleFactory] to be
 * passed into the [TealiumConfig].
 *
 * This object will by default have accessors for all [ModuleFactory] implementations provided by
 * the Tealium Core SDK. External modules are expected to register extension functions on the [Modules]
 * object for creating/configuring them. For Java users, non-core modules will have their own documented
 * entry points.
 *
 * Any methods that accept code blocks for configuration will supply the relevant configuration builder
 * which must be returned at the end of the block, with no further updates after the fact. It should
 * also be noted that configuration set using these methods will override any that come from local or
 * remote settings sources.
 *
 * Example usage:
 * ```kotlin
 * val config = TealiumConfig(app, modules = listOf(
 *        Modules.collect(),
 *        // other optional modules
 *    )
 * )
 * ```
 */
object Modules {

    /**
     * Returns a configured [ModuleFactory] for enabling the Collect Dispatcher Module.
     */
    @JvmStatic
    fun collect(): ModuleFactory {
        return CollectDispatcher.Factory()
    }

    /**
     * Returns a configured [ModuleFactory] for enabling the Collect Dispatcher Module.
     *
     * The [enforcedSettings] will be set for the lifetime of the [Tealium] instance that this [ModuleFactory]
     * is loaded in, and these settings will override any that come from other local/remote sources.
     *
     * @param enforcedSettings Collect dispatcher settings that should override any from any other settings source
     */
    @JvmStatic
    fun collect(enforcedSettings: (CollectDispatcherSettingsBuilder) -> CollectDispatcherSettingsBuilder): ModuleFactory {
        val enforcedSettingsBuilder = enforcedSettings.invoke(CollectDispatcherSettingsBuilder())
        return CollectDispatcher.Factory(enforcedSettingsBuilder)
    }

    /**
     * Returns a configured [ModuleFactory] for enabling the Visitor Service Module.
     */
    @JvmStatic
    fun visitorService(): ModuleFactory = VisitorServiceImpl.Factory()

    /**
     * Returns a configured [ModuleFactory] for enabling the Visitor Service Module.
     *
     * The [enforcedSettings] will be set for the lifetime of the [Tealium] instance that this [ModuleFactory]
     * is loaded in, and these settings will override any that come from other local/remote sources.
     *
     * @param enforcedSettings Visitor Service settings that should override any from any other settings source
     */
    @JvmStatic
    fun visitorService(enforcedSettings: (VisitorServiceSettingsBuilder) -> VisitorServiceSettingsBuilder): ModuleFactory {
        val enforcedSettingsBuilder = enforcedSettings(VisitorServiceSettingsBuilder())
        return VisitorServiceImpl.Factory(enforcedSettingsBuilder)
    }

    /**
     * Collects data related to the current connectivity type of the device.
     *
     * @see Dispatch.Keys.CONNECTION_TYPE
     */
    @JvmStatic
    fun connectivityCollector(): ModuleFactory =
        com.tealium.core.internal.modules.ConnectivityCollector.Factory

    /**
     * Collects data related to the user's device.
     */
    @JvmStatic
    fun deviceDataCollector(): ModuleFactory =
        com.tealium.core.internal.modules.DeviceDataCollector.Factory

    /**
     * Collects data related to the app package.
     */
    @JvmStatic
    fun appDataCollector(): ModuleFactory =
        com.tealium.core.internal.modules.AppDataCollector.Factory

    /**
     * Collects data that has been persisted into the Tealium data layer
     *
     * **Note.** This module is automatically added. This method is provided to allow customizing ordering of
     * modules if required.
     *
     * @see Tealium.dataLayer
     */
    @JvmStatic
    fun dataLayer(): ModuleFactory =
        com.tealium.core.internal.modules.datalayer.DataLayerModule

    /**
     * Collects the Trace Id to support Tealium Trace
     *
     * @see Dispatch.Keys.TEALIUM_TRACE_ID
     * @see Tealium.trace
     */
    @JvmStatic
    fun trace(): ModuleFactory =
        com.tealium.core.internal.modules.trace.TraceManagerModule.Factory

    /**
     * Collects the Tealium required data (Account, profile etc)
     *
     * **Note.** This module is automatically added. This method is provided to allow customizing ordering of
     * modules if required.
     */
    @JvmStatic
    fun tealiumCollector(): ModuleFactory =
        com.tealium.core.internal.modules.TealiumCollector.Factory

    /**
     * Returns a configured [ModuleFactory] for enabling the DeepLink Handler Module.
     */
    @JvmStatic
    fun deepLink(): ModuleFactory =
        DeepLinkHandlerModule.Factory()

    /**
     * Returns a configured [ModuleFactory] for enabling the DeepLink Handler Module.
     *
     * The [enforcedSettings] will be set for the lifetime of the [Tealium] instance that this [ModuleFactory]
     * is loaded in, and these settings will override any that come from other local/remote sources.
     *
     * @param enforcedSettings DeepLink Handler settings that should override any from any other settings source
     */
    @JvmStatic
    fun deepLink(enforcedSettings: (DeepLinkSettingsBuilder) -> DeepLinkSettingsBuilder): ModuleFactory {
        val enforcedSettingsBuilder = enforcedSettings(DeepLinkSettingsBuilder())
        return DeepLinkHandlerModule.Factory(enforcedSettingsBuilder)
    }
}
