package com.tealium.core.api.settings

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.LogLevel
import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.misc.TimeFrameUtils.inSeconds
import com.tealium.core.internal.settings.CoreSettingsImpl

class CoreSettingsBuilder {
    private val builder = DataObject.Builder()
    fun setLogLevel(logLevel: LogLevel) = apply {
        builder.put(CoreSettingsImpl.KEY_LOG_LEVEL, logLevel)
    }

    fun setDataSource(dataSource: String) = apply {
        builder.put(CoreSettingsImpl.KEY_DATA_SOURCE, dataSource)
    }

    fun setBatchSize(batchSize: Int) = apply {
        builder.put(
            CoreSettingsImpl.KEY_BATCH_SIZE, batchSize
                .coerceIn(0, CoreSettingsImpl.MAX_BATCH_SIZE)
        )
    }

    fun setMaxQueueSize(queueSize: Int) = apply {
        builder.put(CoreSettingsImpl.KEY_MAX_QUEUE_SIZE, queueSize)
    }

    fun setExpiration(expiration: TimeFrame) = apply {
        builder.put(CoreSettingsImpl.KEY_EXPIRATION, expiration.inSeconds())
    }

    fun setBatterySaver(batterySaver: Boolean) = apply {
        builder.put(CoreSettingsImpl.KEY_BATTERY_SAVER, batterySaver)
    }

    fun setWifiOnly(wifiOnly: Boolean) = apply {
        builder.put(CoreSettingsImpl.KEY_WIFI_ONLY, wifiOnly)
    }

    fun setRefreshInterval(refreshInterval: TimeFrame) = apply {
        builder.put(CoreSettingsImpl.KEY_REFRESH_INTERVAL, refreshInterval.inSeconds())
    }

    fun setDeepLinkTrackingEnabled(deepLinkTrackingEnabled: Boolean) = apply {
        builder.put(CoreSettingsImpl.KEY_DEEPLINK_TRACKING_ENABLED, deepLinkTrackingEnabled)
    }

    fun setDisableLibrary(disableLibrary: Boolean) = apply {
        builder.put(CoreSettingsImpl.KEY_DISABLE_LIBRARY, disableLibrary)
    }

    fun setVisitorIdentityKey(key: String) = apply {
        builder.put(CoreSettingsImpl.KEY_VISITOR_IDENTITY_KEY, key)
    }

    fun build(): DataObject {
        return builder.build()
    }
}