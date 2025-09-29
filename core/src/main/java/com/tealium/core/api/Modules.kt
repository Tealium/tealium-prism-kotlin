package com.tealium.core.api

import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.settings.CollectSettingsBuilder
import com.tealium.core.api.settings.DeepLinkSettingsBuilder
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.internal.modules.AppDataModule
import com.tealium.core.internal.modules.ConnectivityDataModule
import com.tealium.core.internal.modules.DeviceDataModule
import com.tealium.core.internal.modules.TealiumDataModule
import com.tealium.core.internal.modules.collect.CollectModule
import com.tealium.core.internal.modules.datalayer.DataLayerModule
import com.tealium.core.internal.modules.deeplink.DeepLinkModule
import com.tealium.core.internal.modules.time.TimeDataModule
import com.tealium.core.internal.modules.trace.TraceModule

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
     * Object to hold the [ModuleFactory.moduleType] constants.
     */
    object Types {
        const val APP_DATA = "AppData"
        const val COLLECT = "Collect"
        const val CONNECTIVITY_DATA = "ConnectivityData"
        const val DATA_LAYER = "DataLayer"
        const val DEEP_LINK = "DeepLink"
        const val DEVICE_DATA = "DeviceData"
        const val TEALIUM_DATA = "TealiumData"
        const val TIME_DATA = "TimeData"
        const val TRACE = "Trace"
    }

    /**
     * Returns a configured [ModuleFactory] for enabling the Collect Module.
     */
    @JvmStatic
    fun collect(): ModuleFactory {
        return CollectModule.Factory()
    }

    /**
     * Returns a configured [ModuleFactory] for enabling the Collect Module.
     *
     * The [enforcedSettings] will be set for the lifetime of the [Tealium] instance that this [ModuleFactory]
     * is loaded in, and these settings will override any that come from other local/remote sources.
     *
     * @param enforcedSettings Collect dispatcher settings that should override any from any other settings source
     */
    @JvmStatic
    fun collect(enforcedSettings: (CollectSettingsBuilder) -> CollectSettingsBuilder): ModuleFactory =
        collect(*arrayOf(enforcedSettings))

    /**
     * Returns a configured [ModuleFactory] for enabling the Collect Module.
     *
     * The [enforcedSettings] will be set for the lifetime of the [Tealium] instance that this [ModuleFactory]
     * is loaded in, and these settings will override any that come from other local/remote sources.
     *
     * @param enforcedSettings A variable number of Collect dispatcher settings to configure multiple instances, that should override any from any other settings source
     */
    @JvmStatic
    fun collect(vararg enforcedSettings: (CollectSettingsBuilder) -> CollectSettingsBuilder): ModuleFactory {
        val configObjects = enforcedSettings.map { block ->
            val enforcedSettingsBuilder = block.invoke(CollectSettingsBuilder())
            enforcedSettingsBuilder.build()
        }
        return CollectModule.Factory(configObjects)
    }

    /**
     * Collects data related to the current connectivity type of the device.
     *
     * @see Dispatch.Keys.CONNECTION_TYPE
     */
    @JvmStatic
    fun connectivityData(): ModuleFactory =
        ConnectivityDataModule.Factory

    /**
     * Collects data related to the user's device.
     */
    @JvmStatic
    fun deviceData(): ModuleFactory =
        DeviceDataModule.Factory

    /**
     * Collects data related to the app package.
     */
    @JvmStatic
    fun appData(): ModuleFactory =
        AppDataModule.Factory

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
        DataLayerModule

    /**
     * Collects the Trace Id to support Tealium Trace
     *
     * @see Dispatch.Keys.TEALIUM_TRACE_ID
     * @see Tealium.trace
     */
    @JvmStatic
    fun trace(): ModuleFactory =
        TraceModule.Factory

    /**
     * Collects the Tealium required data (Account, profile etc)
     *
     * **Note.** This module is automatically added. This method is provided to allow customizing ordering of
     * modules if required.
     */
    @JvmStatic
    fun tealiumData(): ModuleFactory =
        TealiumDataModule.Factory

    /**
     * Returns a configured [ModuleFactory] for enabling the DeepLink Module.
     */
    @JvmStatic
    fun deepLink(): ModuleFactory =
        DeepLinkModule.Factory()

    /**
     * Returns a configured [ModuleFactory] for enabling the DeepLink Module.
     *
     * The [enforcedSettings] will be set for the lifetime of the [Tealium] instance that this [ModuleFactory]
     * is loaded in, and these settings will override any that come from other local/remote sources.
     *
     * @param enforcedSettings DeepLink settings that should override any from any other settings source
     */
    @JvmStatic
    fun deepLink(enforcedSettings: (DeepLinkSettingsBuilder) -> DeepLinkSettingsBuilder): ModuleFactory {
        val enforcedSettingsBuilder = enforcedSettings(DeepLinkSettingsBuilder())
        return DeepLinkModule.Factory(enforcedSettingsBuilder)
    }

    /**
     * Returns a factory for creating the TimeData module, used to add a variety of additional
     * time-based data to each [Dispatch]
     */
    @JvmStatic
    fun timeData() : ModuleFactory =
        TimeDataModule.Factory

}
