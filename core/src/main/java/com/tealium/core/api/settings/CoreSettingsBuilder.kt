package com.tealium.core.api.settings

import com.tealium.core.LogLevel
import com.tealium.core.internal.settings.CoreSettings

/**
 * Enables the configuration, at runtime, of the Core settings.
 *
 * Note. Any configuration set here will override any configuration provided by the local or remote
 * settings files and will no longer be overridable remotely.
 */
class CoreSettingsBuilder : ModuleSettingsBuilder(CoreSettings.moduleName) {

    /**
     * Sets the [LogLevel] for this Tealium instance
     */
    fun setLogLevel(logLevel: LogLevel) = apply {
        builder.put(CoreSettings.KEY_LOG_LEVEL, logLevel)
    }

    /**
     * Sets the Data Source for this Tealium instance
     */
    fun setDataSource(dataSource: String) = apply {
        builder.put(CoreSettings.KEY_DATA_SOURCE, dataSource)
    }

    /**
     * Sets the number of events to send in each batch for this Tealium instance
     */
    fun setBatchSize(batchSize: Int) = apply {
        if (batchSize > CoreSettings.maxBatchSize) {
            builder.put(CoreSettings.KEY_BATCH_SIZE, CoreSettings.maxBatchSize)
        } else {
            builder.put(CoreSettings.KEY_BATCH_SIZE, batchSize)
        }
    }

    /**
     * Sets the maximum number of events to be held in the queue for this Tealium instance. Once
     * this limit is exceeded then events will be evicted on an oldest-first policy.
     */
    fun setMaxQueueSize(queueSize: Int) = apply {
        builder.put(CoreSettings.KEY_MAX_QUEUE_SIZE, queueSize)
    }

    /**
     * Sets the length of time to maintain events before evicting them from the queue.
     */
    // TODO - change signature, or rename this to specify the unit
    fun setExpiration(expiration: Int) = apply {
        builder.put(CoreSettings.KEY_EXPIRATION, expiration)
    }

    /**
     * Sets whether to enabled battery saver mode for this Tealium instance
     */
    fun setBatterySaver(batterySaver: Boolean) = apply {
        builder.put(CoreSettings.KEY_BATTERY_SAVER, batterySaver)
    }

    /**
     * Sets whether to limit connectivity features to only use Wi-Fi based connections.
     */
    fun setWifiOnly(wifiOnly: Boolean) = apply {
        builder.put(CoreSettings.KEY_WIFI_ONLY, wifiOnly)
    }

    /**
     * Sets the refresh interval for retrieving updated SDK Settings.
     */
    // TODO - change signature, or rename this to specify the unit
    fun setRefreshInterval(refreshInterval: Int) = apply {
        builder.put(CoreSettings.KEY_REFRESH_INTERVAL, refreshInterval)
    }

    /**
     * Sets whether ot not the SDK should automatically listen for deep links, and store the deep
     * link url and params in the data layer.
     *
     * Note. This feature is required to support mobile trace when launched via a deep link.
     */
    fun setDeepLinkTrackingEnabled(deepLinkTrackingEnabled: Boolean) = apply {
        builder.put(CoreSettings.KEY_DEEPLINK_TRACKING_ENABLED, deepLinkTrackingEnabled)
    }

    /**
     * Sets whether or not this Tealium instance has been disabled or not.
     */
    fun setDisableLibrary(disableLibrary: Boolean) = apply {
        builder.put(CoreSettings.KEY_DISABLE_LIBRARY, disableLibrary)
    }

    /**
     * Sets the key to look for in the DataLayer to trigger a change in identity.
     */
    fun setVisitorIdentityKey(key: String) = apply {
        builder.put(CoreSettings.KEY_VISITOR_IDENTITY_KEY, key)
    }
}