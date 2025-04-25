package com.tealium.core.internal.network

import com.tealium.core.api.barriers.BarrierFactory
import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.barriers.ConfigurableBarrier
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.internal.modules.collect.CollectDispatcher
import com.tealium.core.internal.settings.CoreSettingsImpl

class ConnectivityBarrier(
    private val connectivity: Connectivity,
    private var wifiOnly: Boolean = false
) : ConfigurableBarrier {

    constructor(
        context: TealiumContext,
        configuration: DataObject
    ) : this(
        context.network,
        configuration.wifiOnly
    )

    override val id: String = BARRIER_ID
    override val onState: Observable<BarrierState>
        get() = connectivity.onConnectionStatusUpdated.map(::connectionSatisfied)

    private fun connectionSatisfied(status: Connectivity.Status): BarrierState {
        return if (status == Connectivity.Status.Connected && satisfiesWifi()) {
            BarrierState.Open
        } else {
            BarrierState.Closed
        }
    }

    override fun updateConfiguration(configuration: DataObject) {
        wifiOnly = configuration.wifiOnly
    }

    /**
     * Checks if the configuration requires WiFi connectivity, and
     */
    private fun satisfiesWifi(): Boolean =
        !wifiOnly || connectivity.connectionType() == Connectivity.ConnectivityType.WIFI


    companion object {
        const val BARRIER_ID = "ConnectivityBarrier"

        private val DataObject.wifiOnly: Boolean
            get() = this.getBoolean(CoreSettingsImpl.KEY_WIFI_ONLY) ?: false
    }

    class Factory(
        private val defaultScope: Set<BarrierScope> = setOf(
            BarrierScope.Dispatcher(CollectDispatcher.moduleName)
        )
    ) : BarrierFactory {
        override val id: String = BARRIER_ID
        override fun defaultScope(): Set<BarrierScope> = defaultScope
        override fun create(context: TealiumContext, configuration: DataObject): ConfigurableBarrier =
            ConnectivityBarrier(context, configuration)
    }
}