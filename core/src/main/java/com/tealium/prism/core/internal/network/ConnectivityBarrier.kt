package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.barriers.BarrierFactory
import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.barriers.ConfigurableBarrier
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.network.Connectivity
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject

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

    private fun connectionSatisfied(status: Connectivity.Status, wifiOnly: Boolean): BarrierState =
        if (status.isAssumedConnected && isAllowedByWifiOnly(status, wifiOnly)) {
            BarrierState.Open
        } else {
            BarrierState.Closed
        }

    /** wifi_only only rules out a network known to be cellular. */
    private fun isAllowedByWifiOnly(status: Connectivity.Status, wifiOnly: Boolean): Boolean {
        if (!wifiOnly || status !is Connectivity.Status.Connected) return true
        return status.connectivityType != Connectivity.ConnectivityType.CELLULAR
    }

    /** Flushable whenever connectivity is assumed; wifi_only does not apply to flushes. */
    override val isFlushable: Observable<Boolean>
        get() = connectivityStatus.map { status -> status.isAssumedConnected }

    override fun updateConfiguration(configuration: DataObject) {
        wifiOnly.onNext(configuration.wifiOnly)
    }

    companion object {
        const val BARRIER_ID = "ConnectivityBarrier"
        const val KEY_WIFI_ONLY = "wifi_only"

        private val DataObject.wifiOnly: Boolean
            get() = this.getBoolean(KEY_WIFI_ONLY) ?: false
    }

    class Factory(
        private val defaultScope: BarrierScope =
            BarrierScope.Dispatchers(Modules.Types.COLLECT),
        private val enforcedSettings: DataObject = DataObject.EMPTY_OBJECT
    ) : BarrierFactory {
        override val id: String = BARRIER_ID
        override fun defaultScope(): BarrierScope = defaultScope
        override fun create(
            context: TealiumContext,
            configuration: DataObject
        ): ConfigurableBarrier =
            ConnectivityBarrier(context, configuration)
        
        override fun getEnforcedSettings(): DataObject = enforcedSettings
    }
}