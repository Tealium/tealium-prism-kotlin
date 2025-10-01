package com.tealium.prism.core.internal.modules

import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.network.Connectivity
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.settings.modules.ConnectivityDataSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext

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

    override val id: String = Modules.Types.CONNECTIVITY_DATA
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    class Factory(
        settings: DataObject? = null
    ) : ModuleFactory {
        private val enforcedSettings: List<DataObject> =
            settings?.let { listOf(it) } ?: emptyList()

        constructor(settingsBuilder: ConnectivityDataSettingsBuilder) : this(settingsBuilder.build())

        override fun getEnforcedSettings(): List<DataObject> = enforcedSettings

        override val moduleType: String = Modules.Types.CONNECTIVITY_DATA

        override fun create(
            moduleId: String,
            context: TealiumContext,
            configuration: DataObject
        ): Module {
            return ConnectivityDataModule(context.network.connectionStatus)
        }
    }
}
