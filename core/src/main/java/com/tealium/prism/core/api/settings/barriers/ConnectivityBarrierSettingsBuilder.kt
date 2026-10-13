package com.tealium.prism.core.api.settings.barriers

import com.tealium.prism.core.internal.network.ConnectivityBarrier

/**
 * A builder used to configure the [ConnectivityBarrier] settings.
 */
class ConnectivityBarrierSettingsBuilder : BarrierSettingsBuilder<ConnectivityBarrierSettingsBuilder>() {
    /**
     * Set whether to hold dispatching back while the device is known to be on a cellular
     * connection. Wi-Fi, Ethernet and connections of unknown type are allowed.
     *
     * @param wifiOnly Whether to avoid cellular connections. Default is false.
     * @return The builder instance for method chaining.
     */
    fun setWifiOnly(wifiOnly: Boolean) = apply {
        configurationBuilder.put(ConnectivityBarrier.KEY_WIFI_ONLY, wifiOnly)
    }
}
