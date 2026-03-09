package com.tealium.prism.core.internal.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.network.Connectivity
import com.tealium.prism.core.api.network.Connectivity.ConnectivityType
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.logger.nonNullMessage

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
        .build(),
    private val statusSubject: StateSubject<Connectivity.Status> =
        Observables.stateSubject(Connectivity.Status.Unknown),
    private val logger: Logger
) : Connectivity, ConnectivityManager.NetworkCallback() {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val connectionStatus: ObservableState<Connectivity.Status>
        get() = statusSubject.asObservableState()

    override fun isConnected(): Boolean =
        statusSubject.value is Connectivity.Status.Connected

    override fun connectionType(): ConnectivityType =
        getConnectionType(connectivityManager.activeNetwork)

    private fun getNetworkCapabilities(network: Network?): NetworkCapabilities? = try {
        connectivityManager.getNetworkCapabilities(network)
    } catch (ex: Exception) {
        logger.warn(
            BuildConfig.TAG,
            "Error retrieving network capabilities, %s",
            ex.nonNullMessage()
        )
        null
    }

    private fun getConnectionType(network: Network?): ConnectivityType {
        val capabilities = getNetworkCapabilities(network) ?: return ConnectivityType.UNKNOWN

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectivityType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectivityType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectivityType.ETHERNET
            else -> ConnectivityType.UNKNOWN
        }
    }

    override fun onAvailable(network: Network) {
        super.onAvailable(network)

        val connectionType = getConnectionType(network)
        notifyConnectionUpdated(Connectivity.Status.Connected(connectionType))
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
