package com.tealium.core.api

import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.consent.ConsentManagementAdapter
import com.tealium.core.api.settings.CollectDispatcherSettingsBuilder
import com.tealium.core.api.settings.ConsentSettingsBuilder
import com.tealium.core.api.settings.VisitorServiceSettingsBuilder
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.internal.modules.VisitorServiceImpl
import com.tealium.core.internal.modules.collect.CollectDispatcher
import com.tealium.core.internal.modules.consent.ConsentModule

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
     * Returns a configured [ModuleFactory] for enabling the Consent Management.
     *
     * @param cmp Required [ConsentManagementAdapter] implementation for delegating consent decisions to
     */
    @JvmStatic
    fun consent(cmp: ConsentManagementAdapter): ModuleFactory {
        return ConsentModule.Factory(cmp)
    }

    /**
     * Returns a configured [ModuleFactory] for enabling the Consent Management.
     *
     * The [enforcedSettings] will be set for the lifetime of the [Tealium] instance that this [ModuleFactory]
     * is loaded in, and these settings will override any that come from other local/remote sources.
     *
     * @param cmp Required [ConsentManagementAdapter] implementation for delegating consent decisions to
     * @param enforcedSettings Consent settings that should override any from any other settings source
     */
    @JvmStatic
    fun consent(cmp: ConsentManagementAdapter, enforcedSettings: (ConsentSettingsBuilder) -> ConsentSettingsBuilder): ModuleFactory {
        val enforcedSettingsBuilder = enforcedSettings(ConsentSettingsBuilder())
        return ConsentModule.Factory(cmp, enforcedSettingsBuilder)
    }

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
}
