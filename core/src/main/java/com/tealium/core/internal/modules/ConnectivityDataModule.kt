package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.api.Modules
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext

/**
 * Collects data related to the current connectivity type of the device.
 *
 * @see Dispatch.Keys.CONNECTION_TYPE
 */
class ConnectivityDataModule(
    private val connectivity: ObservableState<Connectivity.Status>,
) : Collector {

    override fun collect(dispatchContext: DispatchContext): DataObject {
        val type = when (val connectionStatus = connectivity.value) {
            is Connectivity.Status.Connected -> connectionStatus.connectivityType.type
            is Connectivity.Status.NotConnected -> "none"
            is Connectivity.Status.Unknown -> "unknown"
        }
        return DataObject.create {
            put(Dispatch.Keys.CONNECTION_TYPE, type)
        }
    }

    override val id: String = Modules.Ids.CONNECTIVITY_DATA
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    object Factory : ModuleFactory {
        override val id: String = Modules.Ids.CONNECTIVITY_DATA

        override fun create(context: TealiumContext, configuration: DataObject): Module {
            return ConnectivityDataModule(context.network.connectionStatus)
        }
    }
}
