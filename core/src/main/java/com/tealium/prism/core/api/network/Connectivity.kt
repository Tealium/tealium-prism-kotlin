package com.tealium.prism.core.api.network

import com.tealium.prism.core.api.pubsub.ObservableState

/**
 * Provides access to the current connectivity status of the device either synchronously through
 * calling [isConnected] or by observing through [connectionStatus].
 */
interface Connectivity {

    /**
     * Signifies whether the device has a network available for making outbound connections.
     *
     * @return true if the device has valid connectivity; otherwise false
     */
    fun isConnected(): Boolean

    /**
     * Utility to return the capability of the current active network.
     *
     * Where a network may have more than one of the [ConnectivityType]'s listed, then they will be
     * returned in the order given in the [ConnectivityType] enum.
     *
     * @return the current type of connectivity.
     */
    fun connectionType(): ConnectivityType

    /**
     * Observable flow of connectivity statuses, enabling reactivity to network status changes
     */
    val connectionStatus: ObservableState<Status>

    /**
     * This class defines the possible statuses of device connectivity.
     */
    sealed class Status {
        object NotConnected : Status()
        object Unknown : Status()
        data class Connected(val connectivityType: ConnectivityType) : Status()
    }

    /**
     * This class defines the possible network connectivity types.
     */
    enum class ConnectivityType(val type: String) {
        WIFI("wifi"),
        CELLULAR("cellular"),
        ETHERNET("ethernet"),
        UNKNOWN("unknown")
    }
}