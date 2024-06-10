package com.tealium.core.internal.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.tealium.core.BuildConfig
import com.tealium.core.api.Scheduler
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.network.Connectivity.ConnectivityType
import com.tealium.core.internal.observables.StateSubject
import com.tealium.core.internal.observables.ObservableState
import com.tealium.core.internal.observables.Observables

/**
 * The [ConnectivityRetriever] is the default implementation of [Connectivity], making connectivity
 * status easily accessible.
 *
 */
class ConnectivityRetriever(
    context: Context,
    private val scheduler: Scheduler,
    private val request: NetworkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).build(),
    private val statusSubject: StateSubject<Connectivity.Status> =
        Observables.stateSubject(Connectivity.Status.NotConnected),
    private val logger: Logger? = null
) : Connectivity, ConnectivityManager.NetworkCallback() {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val onConnectionStatusUpdated: ObservableState<Connectivity.Status>
        get() = statusSubject.asObservableState()

    internal val activeNetworkCapabilities: NetworkCapabilities?
        get() = try {
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        } catch (ex: Exception) {
            logger?.warn?.log(BuildConfig.TAG, "Error retrieving active network capabilities, ${ex.message}")
            null
        }

    override fun isConnected(): Boolean =
        statusSubject.value == Connectivity.Status.Connected

    /**
     * Retrieves the type of the active network connection.
     */
    override fun connectionType(): ConnectivityType {
        val capabilities = activeNetworkCapabilities ?: return ConnectivityType.UNKNOWN

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectivityType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectivityType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectivityType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectivityType.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> ConnectivityType.BLUETOOTH
            else -> ConnectivityType.UNKNOWN
        }
    }

    override fun onAvailable(network: Network) {
        super.onAvailable(network)
        notifyConnectionUpdated(Connectivity.Status.Connected)
    }

    override fun onLosing(network: Network, maxMsToLive: Int) {
        super.onLosing(network, maxMsToLive)
        notifyConnectionUpdated(Connectivity.Status.Unknown)
    }

    override fun onLost(network: Network) {
        super.onLost(network)
        notifyConnectionUpdated(Connectivity.Status.NotConnected)
    }

    override fun onUnavailable() {
        super.onUnavailable()
        notifyConnectionUpdated(Connectivity.Status.NotConnected)
    }

    private fun notifyConnectionUpdated(status: Connectivity.Status) {
        scheduler.execute {
            statusSubject.onNext(status)
        }
    }

    fun subscribe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(this)
        } else {
            connectivityManager.registerNetworkCallback(
                request,
                this
            )
        }
    }

    fun unsubscribe() {
        connectivityManager.unregisterNetworkCallback(this)
    }
}
