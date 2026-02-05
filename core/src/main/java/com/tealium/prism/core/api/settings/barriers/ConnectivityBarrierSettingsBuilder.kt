package com.tealium.prism.core.api.settings.barriers

import com.tealium.prism.core.internal.network.ConnectivityBarrier

/**
 * A builder used to configure the [ConnectivityBarrier] settings.
 */
class ConnectivityBarrierSettingsBuilder : BarrierSettingsBuilder<ConnectivityBarrierSettingsBuilder>() {
    /**
     * Set whether to restrict dispatching to WiFi connections only.
     * When enabled, dispatches will be blocked when only cellular connection is available.
     * 
     * @param wifiOnly Whether to allow only WiFi connections. Default is false.
     * @return The builder instance for method chaining.
     */
    fun setWifiOnly(wifiOnly: Boolean) = apply {
        configurationBuilder.put(ConnectivityBarrier.KEY_WIFI_ONLY, wifiOnly)
    }
}
