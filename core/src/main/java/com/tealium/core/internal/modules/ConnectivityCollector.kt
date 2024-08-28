package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.network.Connectivity

/**
 * Collects data related to the current connectivity type of the device.
 *
 * @see Dispatch.Keys.CONNECTION_TYPE
 */
class ConnectivityCollector(
    private val connectivity: Connectivity,
) : Collector {

    override fun collect(): TealiumBundle {
        return TealiumBundle.create {
            put(Dispatch.Keys.CONNECTION_TYPE, connectivity.connectionType().type)
        }
    }

    override val id: String
        get() = Factory.id
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    object Factory : ModuleFactory {
        override val id: String
            get() = "Connectivity"

        override fun create(context: TealiumContext, settings: TealiumBundle): Module {
            return ConnectivityCollector(context.network)
        }
    }
}
