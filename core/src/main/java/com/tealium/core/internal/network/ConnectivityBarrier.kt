package com.tealium.core.internal.network

import com.tealium.core.api.barriers.BarrierFactory
import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.barriers.ConfigurableBarrier
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.internal.modules.collect.CollectDispatcher
import com.tealium.core.internal.settings.CoreSettingsImpl

class ConnectivityBarrier(
    private val connectivityStatus: Observable<Connectivity.Status>,
    private val wifiOnly: StateSubject<Boolean> = Observables.stateSubject(false)
) : ConfigurableBarrier {

    constructor(
        context: TealiumContext,
        configuration: DataObject
    ) : this(
        context.network.connectionStatus,
        Observables.stateSubject(configuration.wifiOnly)
    )

    override val id: String = BARRIER_ID
    override fun onState(dispatcherId: String): Observable<BarrierState> =
        connectivityStatus.combine(wifiOnly) { status, wifiOnly ->
            connectionSatisfied(status, wifiOnly)
        }.distinct()

    private fun connectionSatisfied(status: Connectivity.Status, wifiOnly: Boolean): BarrierState {
        return if (status is Connectivity.Status.Connected && satisfiesWifi(status.connectivityType, wifiOnly)) {
            BarrierState.Open
        } else {
            BarrierState.Closed
        }
    }

    override val isFlushable: Observable<Boolean>
        get() = connectivityStatus.map { status -> status is Connectivity.Status.Connected }

    override fun updateConfiguration(configuration: DataObject) {
        wifiOnly.onNext(configuration.wifiOnly)
    }

    private fun satisfiesWifi(connectionType: Connectivity.ConnectivityType, wifiOnly: Boolean): Boolean {
        return !wifiOnly
                || connectionType == Connectivity.ConnectivityType.WIFI
                || connectionType == Connectivity.ConnectivityType.ETHERNET
    }

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
        override fun create(
            context: TealiumContext,
            configuration: DataObject
        ): ConfigurableBarrier =
            ConnectivityBarrier(context, configuration)
    }
}