package com.tealium.prism.core.internal.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.network.Connectivity
import com.tealium.prism.core.api.network.Connectivity.ConnectivityType
import com.tealium.prism.core.api.network.Connectivity.Status
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.logger.nonNullMessage

/**
 * The [ConnectivityRetriever] is the default implementation of [Connectivity], making connectivity
 * status easily accessible.
 *
 * It tracks the device's default network (the one the OS routes the app's traffic to; on API 24-27
 * with a VPN up, the network underneath the VPN) and derives the [Status] from that network's
 * [NetworkCapabilities]:
 *
 * | Default network                                                      | Status                |
 * |----------------------------------------------------------------------|-----------------------|
 * | none                                                                 | [Status.NotConnected] |
 * | has INTERNET                                                         | [Status.Connected]    |
 * | lacks INTERNET                                                       | [Status.NotConnected] |
 * | not known yet, or registering failed                                 | [Status.Unknown]      |
 *
 * INTERNET is mostly a defensive check: the default network always has it. Up to API 27,
 * getActiveNetwork can also return a split-tunnel VPN without it: the initial state then uses the
 * network under it. (The check runs on every API level; from API 28 it never matches.) VALIDATED
 * (the OS probed a public endpoint successfully) is only logged and never affects the status.
 *
 * [Status.Unknown] means there is no information, so consumers do not hold back sending (a send
 * that fails is not held back waiting for connectivity either). If registering fails there are no
 * further updates and the status is set to [Status.Unknown].
 *
 * [subscribe] registers the callback, then emits the initial state. [subscribe] and
 * [unsubscribe] are called on the provided [scheduler], which owns all state. Callbacks arrive on
 * the system connectivity thread and are posted to it, so changes are applied in the order
 * received.
 *
 * Which reported network is the default is decided by a [NetworkCallbackStrategy] chosen once: from
 * API 24 the system reports it directly, while API 23 only offers a listen on all INTERNET
 * networks, so [LegacyNetworkCallbackStrategy] works it out and can be deleted when API 23 is
 * dropped.
 *
 * Platform behaviour relied on that comes from Android's source rather than its documentation:
 * API 24-25 send no onCapabilitiesChanged for a stable network; a VPN gets INTERNET only with a
 * default route, so split-tunnel VPNs lack it; up to API 27 getActiveNetwork returns the app's VPN
 * while getActiveNetworkInfo describes the network under it; on API 24-27 the default network
 * callback never reports a VPN; an undeclared VPN's capabilities include its underlying
 * transports only from API 29.
 *
 * Possible refinements that are not implemented:
 *  - `onBlockedStatusChanged` (API 29+): the app is barred from the network by Data Saver, Battery
 *    Saver, Doze, App Standby or a lockdown VPN (reasons are system API only). Blocked follows the
 *    app lifecycle, e.g. Data Saver blocks in the background, so gating on it would skip the flush
 *    the SDK triggers on backgrounding.
 *  - `NET_CAPABILITY_VALIDATED` combined with request outcomes: a network can be unvalidated but
 *    working, or validated but broken. This would also cover captive portals.
 *  - `NET_CAPABILITY_NOT_SUSPENDED` (API 28+, cellular only): cleared while the radio cannot move
 *    data, so requests stall until they time out.
 */
class ConnectivityRetriever(
    context: Context,
    private val scheduler: Scheduler,
    private val logger: Logger,
    private val statusSubject: StateSubject<Status> =
        Observables.stateSubject(Status.Unknown)
) : Connectivity, ConnectivityManager.NetworkCallback() {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val strategy: NetworkCallbackStrategy =
        if (hasDefaultNetworkCallback) {
            DefaultNetworkCallbackStrategy()
        } else {
            LegacyNetworkCallbackStrategy()
        }

    // All state is only accessed on the scheduler, except currentType.

    /** The network currently treated as the default one. */
    private var currentNetwork: Network? = null

    /** The capabilities last applied for [currentNetwork]. */
    private var currentCapabilities: NetworkCapabilities? = null

    /** Whether the callback is registered. Used by [subscribe] and [unsubscribe]. */
    private var registered = false

    /** Read from any thread by [connectionType]. */
    @Volatile
    private var currentType = ConnectivityType.UNKNOWN

    private val isCurrentValidated: Boolean
        get() = currentCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

    override val connectionStatus: ObservableState<Status>
        get() = statusSubject.asObservableState()

    override fun isConnected(): Boolean =
        statusSubject.value is Status.Connected

    /**
     * The type of the network last applied, without querying the [ConnectivityManager].
     */
    override fun connectionType(): ConnectivityType = currentType

    // Android delivers these on its own connectivity thread; hop to the scheduler, which owns all
    // state.

    override fun onAvailable(network: Network) {
        scheduler.execute { strategy.onAvailable(network) }
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        scheduler.execute { strategy.onCapabilitiesChanged(network, networkCapabilities) }
    }

    override fun onLost(network: Network) {
        scheduler.execute { strategy.onLost(network) }
    }

    // The "losing" and "unavailable" callbacks are not overridden. "Losing" can refer to a network
    // that is not the default and the network is usable until onLost; "unavailable" is only
    // invoked for requestNetwork with a timeout, never for these registrations.

    /**
     * Registers for updates, then reads and emits the initial state.
     *
     * The initial state is required: nothing is delivered to a registration on a device that is
     * already offline, which would leave the status [Status.Unknown]. It is skipped if
     * registering failed, as the status would then never be updated.
     */
    fun subscribe() {
        if (registered) return

        // Registering first means no change is missed: anything that changes after registering
        // produces a callback, and the callbacks run after the initial state is applied, so the
        // latest state wins.
        if (!register()) return

        val initialState = try {
            readInitialState()
        } catch (ex: Exception) {
            // Typically ACCESS_NETWORK_STATE missing. The status stays Unknown rather than being
            // treated as "no network".
            logger.error(
                LogCategory.CONNECTIVITY,
                "Error reading the initial network state, %s",
                ex.nonNullMessage()
            )
            return
        }

        // Applied outside the try, so only the reads are guarded.
        applyInitialState(initialState)
    }

    fun unsubscribe() {
        if (!registered) return

        registered = false
        connectivityManager.unregisterNetworkCallback(this)
    }

    /**
     * Reads the default network. Changes no state; throws if it cannot be read.
     *
     * A split-tunnel VPN is never reported by the callbacks, so the type of the network under it is
     * used instead.
     */
    private fun readInitialState(): InitialState {
        val network = connectivityManager.activeNetwork ?: return InitialState.NoNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return InitialState.NoNetwork

        if (isSplitTunnelVpn(capabilities)) {
            return connectivityManager.activeNetworkInfoType(connectedOnly = true)
                ?.let { InitialState.SplitTunnelVpn(it) }
                ?: InitialState.NoNetwork
        }

        return InitialState.ActiveNetwork(network, capabilities)
    }

    private fun applyInitialState(state: InitialState) {
        when (state) {
            InitialState.NoNetwork -> {
                clearCurrent()
                emit(Status.NotConnected)
            }

            is InitialState.ActiveNetwork -> adopt(state.network, state.capabilities)

            is InitialState.SplitTunnelVpn -> {
                clearCurrent()
                currentType = state.underlyingType
                emit(Status.Connected(state.underlyingType))
            }
        }
    }

    /** A VPN without INTERNET, which it only gets with a default route. */
    private fun isSplitTunnelVpn(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

    /** Registers the callback. On failure sets the status to [Status.Unknown] and returns false. */
    private fun register(): Boolean {
        try {
            strategy.register()
            registered = true
            return true
        } catch (ex: Exception) {
            // Missing ACCESS_NETWORK_STATE (SecurityException) or the per-app callback limit
            // (IllegalArgumentException on API 24-27, TooManyRequestsException from API 28). No
            // updates will arrive, so the status would go stale and is set to Unknown instead.
            logger.error(
                LogCategory.CONNECTIVITY,
                "Error registering network callback; connectivity is unknown: %s",
                ex.nonNullMessage()
            )
            clearCurrent()
            emit(Status.Unknown)
            return false
        }
    }

    /**
     * Switches the tracked network to [network]. Capabilities of a different network no longer
     * apply. Use [clearCurrent] to stop tracking.
     */
    private fun setCurrent(network: Network) {
        if (network != currentNetwork) {
            currentCapabilities = null
            currentType = ConnectivityType.UNKNOWN
        }
        currentNetwork = network
    }

    /**
     * Forgets the tracked network and its type, including a type set without tracking a network
     * (an initial state from a split-tunnel VPN).
     */
    private fun clearCurrent() {
        currentNetwork = null
        currentCapabilities = null
        currentType = ConnectivityType.UNKNOWN
    }

    private fun adopt(network: Network, capabilities: NetworkCapabilities) {
        setCurrent(network)
        applyCapabilities(capabilities)
    }

    private fun adopt(state: NetworkWithCapabilities) = adopt(state.network, state.capabilities)

    /** Makes [network] current and applies its capabilities, which may arrive separately. */
    private fun adoptAvailable(network: Network) {
        setCurrent(network)

        // API 23-25 send no onCapabilitiesChanged for a stable network, so read them here. From
        // API 26 one always follows, and reading here could apply older data over newer.
        if (!capabilitiesFollowAvailable) {
            connectivityManager.getNetworkCapabilities(network)?.let { applyCapabilities(it) }
        }
    }

    private fun applyCapabilities(capabilities: NetworkCapabilities) {
        currentCapabilities = capabilities

        val type = resolveType(capabilities)
        currentType = type

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        emit(if (hasInternet) Status.Connected(type) else Status.NotConnected)
    }

    /**
     * Resolves the type from the transports, falling back for a VPN network that has none.
     *
     * A VPN's capabilities carry the transports of its underlying networks only if it declared
     * them on API 28, and always from API 29. Otherwise Android's own answer for the app's traffic
     * is the active network info (the VPN's first underlying network, or the system default). That
     * can go stale if the VPN moves to another network without its own capabilities changing; on
     * API 23 that is refreshed by [LegacyNetworkCallbackStrategy.onOtherNetworkChanged].
     */
    private fun resolveType(capabilities: NetworkCapabilities): ConnectivityType {
        val type = typeOfTransports(capabilities)
        val vpnWithoutTransports = type == ConnectivityType.UNKNOWN
                && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        return if (vpnWithoutTransports && !vpnCapabilitiesHaveTransports) {
            connectivityManager.activeNetworkInfoType(connectedOnly = false)
                ?: ConnectivityType.UNKNOWN
        } else {
            type
        }
    }

    private fun typeOfTransports(capabilities: NetworkCapabilities): ConnectivityType = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectivityType.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectivityType.CELLULAR
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectivityType.ETHERNET
        else -> ConnectivityType.UNKNOWN
    }

    private fun emit(status: Status) {
        if (status == statusSubject.value) return

        logger.debug(
            LogCategory.CONNECTIVITY,
            "Connectivity changed to %s (validated: %s)",
            status,
            isCurrentValidated
        )
        statusSubject.onNext(status)
    }

    /**
     * The type of the active network according to the legacy `NetworkInfo` API, or null if there is
     * none (or, with [connectedOnly], if it is not connected).
     *
     * The API is deprecated, but below API 29 it is Android's own answer for the network under a
     * VPN. Read for a VPN whose capabilities lack the transport (below API 29), and for a
     * split-tunnel VPN when reading the initial state (in practice up to API 27).
     */
    @Suppress("DEPRECATION")
    private fun ConnectivityManager.activeNetworkInfoType(connectedOnly: Boolean): ConnectivityType? {
        val info = activeNetworkInfo ?: return null
        if (connectedOnly && !info.isConnected) return null

        return when (info.type) {
            ConnectivityManager.TYPE_WIFI -> ConnectivityType.WIFI
            ConnectivityManager.TYPE_MOBILE -> ConnectivityType.CELLULAR
            ConnectivityManager.TYPE_ETHERNET -> ConnectivityType.ETHERNET
            else -> ConnectivityType.UNKNOWN
        }
    }

    /** A network with its capabilities. */
    private class NetworkWithCapabilities(val network: Network, val capabilities: NetworkCapabilities)

    /** What was read about the default network when subscribing. */
    private sealed interface InitialState {
        /**
         * No active network: none, it went away before its capabilities were read, or a
         * split-tunnel VPN whose underlying network is not connected.
         */
        data object NoNetwork : InitialState

        /** The active network and its capabilities. */
        class ActiveNetwork(val network: Network, val capabilities: NetworkCapabilities) : InitialState

        /**
         * A split-tunnel VPN is active. The callbacks never report it, so only the type of the
         * network under it is known.
         */
        class SplitTunnelVpn(val underlyingType: ConnectivityType) : InitialState
    }

    /**
     * How the default network is tracked on this API level. The retriever owns all state; a
     * strategy registers the platform callback and turns each callback into changes of the tracked
     * network. Chosen once at construction: [DefaultNetworkCallbackStrategy] from API 24,
     * [LegacyNetworkCallbackStrategy] on API 23. The strategies are inner classes so they can
     * change the retriever's private state without widening its visibility.
     */
    private interface NetworkCallbackStrategy {
        fun register()

        fun onAvailable(network: Network)

        fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities)

        fun onLost(network: Network)
    }

    /** API 24+: every callback concerns the default network. */
    @RequiresApi(Build.VERSION_CODES.N)
    private inner class DefaultNetworkCallbackStrategy : NetworkCallbackStrategy {

        override fun register() {
            // From API 28 this follows the app's VPN; on API 24-27 it reports the network
            // underneath it.
            connectivityManager.registerDefaultNetworkCallback(this@ConnectivityRetriever)
        }

        override fun onAvailable(network: Network) {
            adoptAvailable(network)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (network == currentNetwork) applyCapabilities(capabilities)
        }

        override fun onLost(network: Network) {
            if (network != currentNetwork) return

            // A replacement, if any, is announced by its own onAvailable, and the lost network may
            // still be reported as active here.
            clearCurrent()
            emit(Status.NotConnected)
        }
    }

    /**
     * API 23 only: registers with `registerNetworkCallback` and an INTERNET request, which reports
     * every INTERNET network (including VPNs), and works out which one is the default itself by
     * comparing with the active network.
     */
    private inner class LegacyNetworkCallbackStrategy : NetworkCallbackStrategy {

        override fun register() {
            // NetworkRequest.Builder requires NOT_VPN by default, which would hide the VPN that
            // getActiveNetwork returns for this app. VALIDATED is deliberately not required.
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(),
                this@ConnectivityRetriever
            )
        }

        override fun onAvailable(network: Network) {
            if (isKnownNonDefault(network)) {
                onOtherNetworkChanged()
                return
            }

            adoptAvailable(network)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            when (network) {
                currentNetwork -> onCurrentCapabilitiesChanged(network, capabilities)
                // It became the default (e.g. Wi-Fi that just validated); no other event says so.
                connectivityManager.activeNetwork -> adopt(network, capabilities)
                else -> onOtherNetworkChanged()
            }
        }

        private fun onCurrentCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities
        ) {
            // Losing VALIDATED can make another connected network the default without an event for
            // it. VALIDATED is only a trigger here; it is not the status.
            val lostValidated = isCurrentValidated
                    && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val replacement = if (lostValidated) reportableDefaultOtherThan(network) else null
            if (replacement != null) {
                adopt(replacement)
            } else {
                applyCapabilities(capabilities)
            }
        }

        override fun onLost(network: Network) {
            if (network != currentNetwork) {
                onOtherNetworkChanged()
                return
            }

            clearCurrent()

            // A replacement may already be connected without an event, so the default is re-read.
            // The service reports the loss before removing the network, so the lost network itself
            // can still be returned and must not be adopted again.
            val replacement = reportableDefaultOtherThan(network)
            if (replacement != null) {
                adopt(replacement)
            } else {
                emit(Status.NotConnected)
            }
        }

        /**
         * Whether [network] is reported beside a default that the listen also reports. False while
         * nothing is tracked, or while the default is not reportable (none, or a split-tunnel VPN):
         * then the reported network is adopted, so the last reported one is followed.
         */
        private fun isKnownNonDefault(network: Network): Boolean {
            if (currentNetwork == null) return false
            val default = reportableDefault() ?: return false
            return network != default.network
        }

        /**
         * Another network changed. If the current network is a VPN it may now run over that
         * network, so its type is re-resolved (emitting only if it changed).
         */
        private fun onOtherNetworkChanged() {
            val capabilities = currentCapabilities ?: return
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                applyCapabilities(capabilities)
            }
        }

        /**
         * The active network with its capabilities, if the listen reports it. A split-tunnel VPN
         * can be active without INTERNET and is never reported, so it is excluded.
         */
        private fun reportableDefault(): NetworkWithCapabilities? {
            val network = connectivityManager.activeNetwork ?: return null
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            return if (hasInternet) NetworkWithCapabilities(network, capabilities) else null
        }

        private fun reportableDefaultOtherThan(network: Network): NetworkWithCapabilities? =
            reportableDefault()?.takeIf { it.network != network }
    }

    private companion object {
        /** Below API 24 there is no default network callback, so an INTERNET listen is used. */
        @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
        val hasDefaultNetworkCallback: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

        /** From API 26 onCapabilitiesChanged always follows onAvailable. */
        @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
        val capabilitiesFollowAvailable: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

        /** From API 29 a VPN's capabilities always include its underlying transports. */
        @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
        val vpnCapabilitiesHaveTransports: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}
