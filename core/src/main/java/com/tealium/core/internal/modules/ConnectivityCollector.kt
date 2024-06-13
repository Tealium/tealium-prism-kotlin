package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.Collector
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.settings.ModuleSettings

/**
 * Collects data related to the current connectivity type of the device.
 *
 * @see Dispatch.Keys.CONNECTION_TYPE
 */
class ConnectivityCollector(
    private val connectivity: Connectivity,
) : Collector {

    override fun updateSettings(moduleSettings: ModuleSettings): Module? {
        return if (moduleSettings.enabled) this else null
    }

    override fun collect(): TealiumBundle {
        return TealiumBundle.create {
            put(Dispatch.Keys.CONNECTION_TYPE, connectivity.connectionType().type)
        }
    }

    override val name: String
        get() = Factory.name
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    object Factory : ModuleFactory {
        override val name: String
            get() = "Connectivity"

        override fun create(context: TealiumContext, settings: ModuleSettings): Module? {
            if (!settings.enabled) return null

            return ConnectivityCollector(context.network)
        }
    }
}
