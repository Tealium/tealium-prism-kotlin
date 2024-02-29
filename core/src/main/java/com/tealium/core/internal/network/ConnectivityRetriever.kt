package com.tealium.core.internal.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import com.tealium.core.BuildConfig
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.network.Connectivity.ConnectivityType
import com.tealium.core.internal.Singleton
import com.tealium.core.internal.observables.StateSubject
import com.tealium.core.internal.observables.ObservableState
import com.tealium.core.internal.observables.Observables

/**
 * The [ConnectivityRetriever] is the default implementation of [Connectivity], making connectivity
 * status easily accessible.
 *
 * Note. The internal constructor is for testing purposes only, and access to the [ConnectivityRetriever]
 * should typically be via its [Companion] singleton instead as it is safe to share this between
 * Tealium instances
 */
class ConnectivityRetriever internal constructor(
    context: Context,
    private val request: NetworkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).build(),
    private val statusSubject: StateSubject<Connectivity.Status> =
        Observables.stateSubject(Connectivity.Status.NotConnected)
) : Connectivity, ConnectivityManager.NetworkCallback() {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val onConnectionStatusUpdated: ObservableState<Connectivity.Status>
        get() = statusSubject

    internal val activeNetworkCapabilities: NetworkCapabilities?
        get() = try {
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        } catch (ex: Exception) {
            Log.e(BuildConfig.TAG, "Error retrieving active network capabilities, ${ex.message}")
            null
        }

    override fun isConnected(): Boolean =
        statusSubject.value == Connectivity.Status.Connected

    /**
     * Retrieves the type of the active network connection.
     */
    override fun connectionType(): ConnectivityType {
        return activeNetworkCapabilities?.let {
            return when {
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectivityType.WIFI
                it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectivityType.CELLULAR
                it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectivityType.ETHERNET
                it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectivityType.VPN
                it.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> ConnectivityType.BLUETOOTH
                else -> ConnectivityType.UNKNOWN
            }
        } ?: ConnectivityType.UNKNOWN
    }

    init {
        subscribe()
    }

    override fun onAvailable(network: Network) {
        super.onAvailable(network)
        statusSubject.onNext(Connectivity.Status.Connected)
    }

    override fun onLosing(network: Network, maxMsToLive: Int) {
        super.onLosing(network, maxMsToLive)
        statusSubject.onNext(Connectivity.Status.Unknown)
    }

    override fun onLost(network: Network) {
        super.onLost(network)
        statusSubject.onNext(Connectivity.Status.NotConnected)
    }

    override fun onUnavailable() {
        super.onUnavailable()
        statusSubject.onNext(Connectivity.Status.NotConnected)
    }

    private fun subscribe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(this)
        } else {
            connectivityManager.registerNetworkCallback(
                request,
                this
            )
        }
    }

    // TODO unsubscribe on Tealium instance shutdown event
    private fun unsubscribe() {
        connectivityManager.unregisterNetworkCallback(this)
    }

    companion object :
        Singleton<ConnectivityRetriever, Context>({ context -> ConnectivityRetriever(context) })
}
