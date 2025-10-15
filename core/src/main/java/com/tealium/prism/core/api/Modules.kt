package com.tealium.prism.core.api

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.settings.modules.AppDataSettingsBuilder
import com.tealium.prism.core.api.settings.modules.CollectSettingsBuilder
import com.tealium.prism.core.api.settings.modules.ConnectivityDataSettingsBuilder
import com.tealium.prism.core.api.settings.modules.DataLayerSettingsBuilder
import com.tealium.prism.core.api.settings.modules.DeepLinkSettingsBuilder
import com.tealium.prism.core.api.settings.modules.DeviceDataSettingsBuilder
import com.tealium.prism.core.api.settings.modules.ModuleSettingsBuilder
import com.tealium.prism.core.api.settings.modules.TealiumDataSettingsBuilder
import com.tealium.prism.core.api.settings.modules.TimeDataSettingsBuilder
import com.tealium.prism.core.api.settings.modules.TraceSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.modules.AppDataModule
import com.tealium.prism.core.internal.modules.ConnectivityDataModule
import com.tealium.prism.core.internal.modules.DeviceDataModule
import com.tealium.prism.core.internal.modules.ModuleRegistry
import com.tealium.prism.core.internal.modules.TealiumDataModule
import com.tealium.prism.core.internal.modules.collect.CollectModule
import com.tealium.prism.core.internal.modules.datalayer.DataLayerModule
import com.tealium.prism.core.internal.modules.deeplink.DeepLinkModule
import com.tealium.prism.core.internal.modules.time.TimeDataModule
import com.tealium.prism.core.internal.modules.trace.TraceModule

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
 * val config = TealiumConfig.Builder(app, ..., modules = listOf(
 *        Modules.collect(),
 *        // other optional modules
 *    )
 * ).build()
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
     *
     * The [enforcedSettings] will be set for the lifetime of the [Tealium] instance that this [ModuleFactory]
     * is loaded in, and these settings will override any that come from other local/remote sources.
     *
     * @param enforcedSettings
     *  Collect dispatcher settings that should override any from any other settings source.
     *  Pass `null` to initialize this module only when some Local or Remote settings are provided.
     *  Omitting this parameter will initialize the module with its default settings.
     */
    @JvmStatic
    @JvmOverloads
    fun collect(enforcedSettings: ((CollectSettingsBuilder) -> CollectSettingsBuilder)? = defaultSettings()): ModuleFactory {
        val settings = buildSettings(::CollectSettingsBuilder, listOf(enforcedSettings))
        return CollectModule.Factory(settings)
    }

    /**
     * Returns a configured [ModuleFactory] for enabling the Collect Module.
     *
     * The [enforcedSettings] will be set for the lifetime of the [Tealium] instance that this [ModuleFactory]
     * is loaded in, and these settings will override any that come from other local/remote sources.
     *
     * @param enforcedSettings
     *  A variable number of Collect dispatcher settings to configure multiple instances, that should
     *  override any from any other settings source.
     */
    @JvmStatic
    fun collect(enforcedSettings: (CollectSettingsBuilder) -> CollectSettingsBuilder, vararg otherEnforcedSettings: (CollectSettingsBuilder) -> CollectSettingsBuilder): ModuleFactory {
        val settings = buildSettings(::CollectSettingsBuilder, listOf(enforcedSettings) + otherEnforcedSettings.asList())
        return CollectModule.Factory(settings)
    }

    /**
     * Collects data related to the current connectivity type of the device.
     *
     * @param enforcedSettings
     *  ConnectivityData settings that should override any from any other settings source.
     *  Pass `null` to initialize this module only when some Local or Remote settings are provided.
     *  Omitting this parameter will initialize the module with its default settings.
     *
     * @see Dispatch.Keys.CONNECTION_TYPE
     */
    @JvmStatic
    @JvmOverloads
    fun connectivityData(enforcedSettings: ((ConnectivityDataSettingsBuilder) -> ConnectivityDataSettingsBuilder)? = defaultSettings()): ModuleFactory {
        val settings = buildSettings(::ConnectivityDataSettingsBuilder, enforcedSettings)
        return ConnectivityDataModule.Factory(settings)
    }

    /**
     * Collects data related to the user's device.
     *
     * @param enforcedSettings
     *  DeviceData settings that should override any from any other settings source.
     *  Pass `null` to initialize this module only when some Local or Remote settings are provided.
     *  Omitting this parameter will initialize the module with its default settings.
     */
    @JvmStatic
    @JvmOverloads
    fun deviceData(enforcedSettings: ((DeviceDataSettingsBuilder) -> DeviceDataSettingsBuilder)? = defaultSettings()): ModuleFactory {
        val settings = buildSettings(::DeviceDataSettingsBuilder, enforcedSettings)
        return DeviceDataModule.Factory(settings)
    }

    /**
     * Collects data related to the app package.
     *
     * @param enforcedSettings
     *  AppData settings that should override any from any other settings source.
     *  Pass `null` to initialize this module only when some Local or Remote settings are provided.
     *  Omitting this parameter will initialize the module with its default settings.
     */
    @JvmStatic
    @JvmOverloads
    fun appData(enforcedSettings: ((AppDataSettingsBuilder) -> AppDataSettingsBuilder)? = defaultSettings()): ModuleFactory {
        val settings = buildSettings(::AppDataSettingsBuilder, enforcedSettings)
        return AppDataModule.Factory(settings)
    }

    /**
     * Collects data that has been persisted into the Tealium data layer
     *
     * **Note.** This module is automatically added. This method is provided to allow customizing ordering of
     * modules if required.
     *
     * @param enforcedSettings
     *  DataLayer settings that should override any from any other settings source.
     *  Omitting this parameter will initialize the module with its default settings.
     *
     * @see Tealium.dataLayer
     */
    @JvmStatic
    @JvmOverloads
    fun dataLayer(enforcedSettings: (DataLayerSettingsBuilder) -> DataLayerSettingsBuilder = defaultSettings()): ModuleFactory {
        val settings = buildSettings(::DataLayerSettingsBuilder, enforcedSettings)
        return DataLayerModule.Factory(settings)
    }

    /**
     * Collects the Trace Id to support Tealium Trace
     *
     * @param enforcedSettings
     *  Trace settings that should override any from any other settings source.
     *  Pass `null` to initialize this module only when some Local or Remote settings are provided.
     *  Omitting this parameter will initialize the module with its default settings.
     *
     * @see Dispatch.Keys.TEALIUM_TRACE_ID
     * @see Tealium.trace
     */
    @JvmStatic
    @JvmOverloads
    fun trace(enforcedSettings: ((TraceSettingsBuilder) -> TraceSettingsBuilder)? = defaultSettings()): ModuleFactory {
        val settings = buildSettings(::TraceSettingsBuilder, enforcedSettings)
        return TraceModule.Factory(settings)
    }

    /**
     * Collects the Tealium required data (Account, profile etc)
     *
     * **Note.** This module is automatically added. This method is provided to allow customizing ordering of
     * modules if required.
     *
     * @param enforcedSettings
     *  TealiumData settings that should override any from any other settings source.
     *  Omitting this parameter will initialize the module with its default settings.
     */
    @JvmStatic
    @JvmOverloads
    fun tealiumData(enforcedSettings: (TealiumDataSettingsBuilder) -> TealiumDataSettingsBuilder = defaultSettings()): ModuleFactory {
        val settings = buildSettings(::TealiumDataSettingsBuilder, enforcedSettings)
        return TealiumDataModule.Factory(settings)
    }

    /**
     * Returns a configured [ModuleFactory] for enabling the DeepLink Module.
     *
     * The [enforcedSettings] will be set for the lifetime of the [Tealium] instance that this [ModuleFactory]
     * is loaded in, and these settings will override any that come from other local/remote sources.
     *
     * @param enforcedSettings
     *  DeepLink settings that should override any from any other settings source.
     *  Pass `null` to initialize this module only when some Local or Remote settings are provided.
     *  Omitting this parameter will initialize the module with its default settings.
     */
    @JvmStatic
    @JvmOverloads
    fun deepLink(enforcedSettings: ((DeepLinkSettingsBuilder) -> DeepLinkSettingsBuilder)? = defaultSettings()): ModuleFactory {
        val settings = buildSettings(::DeepLinkSettingsBuilder, enforcedSettings)
        return DeepLinkModule.Factory(settings)
    }

    /**
     * Returns a factory for creating the TimeData module, used to add a variety of additional
     * time-based data to each [Dispatch]
     *
     * @param enforcedSettings
     *  TimeData settings that should override any from any other settings source.
     *  Pass `null` to initialize this module only when some Local or Remote settings are provided.
     *  Omitting this parameter will initialize the module with its default settings.
     */
    @JvmStatic
    @JvmOverloads
    fun timeData(enforcedSettings: ((TimeDataSettingsBuilder) -> TimeDataSettingsBuilder)? = defaultSettings()): ModuleFactory {
        val settings = buildSettings(::TimeDataSettingsBuilder, enforcedSettings)
        return TimeDataModule.Factory(settings)
    }

    /**
     * Utility method to simply pass-through any given [ModuleSettingsBuilder], effectively
     * providing only the default settings.
     */
    private fun <T : ModuleSettingsBuilder<T>> defaultSettings(): (T) -> T = { it }

    /**
     * Utility method to create an instance of a [ModuleSettingsBuilder], and optionally configure it
     * using the [enforcedSettings] block.
     *
     * `null` values for [enforcedSettings] will return `null`
     */
    private inline fun <T : ModuleSettingsBuilder<T>> buildSettings(
        settingsBuilderSupplier: () -> T,
        noinline enforcedSettings: ((T) -> T)?
    ): DataObject? {
        if (enforcedSettings == null)
            return null

        val builder = settingsBuilderSupplier.invoke()
        enforcedSettings.invoke(builder)
        return builder.build()
    }

    /**
     * Utility method to create an instance of a [ModuleSettingsBuilder], and optionally configure it
     * using multiple [enforcedSettings] blocks.
     *
     * `null` values for [enforcedSettings] will not produce any settings, and will effectively be
     * omitted from the returned result
     */
    private inline fun <T : ModuleSettingsBuilder<T>> buildSettings(
        settingsBuilderSupplier: () -> T,
        enforcedSettings: List<((T) -> T)?>
    ): List<DataObject> =
        enforcedSettings.mapNotNull {
            buildSettings(settingsBuilderSupplier, it)
        }

    /**
     * Adds additional `ModuleFactory`s to the list of default factories that are added to each
     * `Tealium` instance.
     *
     * Each module added in this list will be added only if the same module wasn't already added in
     * the specific config object. Generally factories added by default will not return any enforced
     * settings, meaning that they will require some local or remote settings to initialize their
     * respective modules.
     *
     * If they contain some settings, instead, their modules will be initialized even if they are
     * not configured elsewhere.
     */
    @JvmStatic
    fun addDefaultModules(modules: List<ModuleFactory>) =
        ModuleRegistry.addDefaultModules(modules)

    /**
     * A list of all [ModuleFactory]'s that are eligible for automatic registration.
     */
    @JvmStatic
    val defaultModules: List<ModuleFactory>
        get() = ModuleRegistry.defaultModules
}
