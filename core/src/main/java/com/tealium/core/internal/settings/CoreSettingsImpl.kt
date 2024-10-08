package com.tealium.core.internal.settings

import com.tealium.core.api.barriers.ScopedBarrier
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.LogLevel
import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.misc.TimeFrameUtils.days
import com.tealium.core.api.misc.TimeFrameUtils.minutes
import com.tealium.core.api.misc.TimeFrameUtils.seconds
import com.tealium.core.api.settings.CoreSettings
import com.tealium.core.api.transform.ScopedTransformation
import com.tealium.core.internal.misc.Converters

class CoreSettingsImpl(
    override val logLevel: LogLevel = DEFAULT_LOG_LEVEL,
    override val dataSource: String? = null,
    override val batchSize: Int = DEFAULT_BATCH_SIZE,
    override val maxQueueSize: Int = DEFAULT_MAX_QUEUE_SIZE,
    override val expiration: TimeFrame = DEFAULT_EXPIRATION_DAYS.days,
    override val batterySaver: Boolean = DEFAULT_BATTERY_SAVER,
    override val wifiOnly: Boolean = DEFAULT_WIFI_ONLY,
    override val refreshInterval: TimeFrame = DEFAULT_REFRESH_INTERVAL_MINUTES.minutes,
    override val deepLinkTrackingEnabled: Boolean = DEFAULT_DEEPLINK_TRACKING_ENABLED,
    override val disableLibrary: Boolean = DEFAULT_DISABLE_LIBRARY,
    override val visitorIdentityKey: String? = null,
    override val barriers: Set<ScopedBarrier> = setOf(),
    override val transformations: Set<ScopedTransformation> = setOf(),
) : CoreSettings {

    companion object {
        const val MODULE_NAME = "core"
        const val KEY_LOG_LEVEL = "log_level"
        const val KEY_DATA_SOURCE = "data_source"
        const val KEY_BATCH_SIZE = "batch_size"
        const val KEY_MAX_QUEUE_SIZE = "max_queue_size"
        const val KEY_EXPIRATION = "expiration"
        const val KEY_BATTERY_SAVER = "battery_saver"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_REFRESH_INTERVAL = "refresh_interval"
        const val KEY_DEEPLINK_TRACKING_ENABLED = "deeplink_tracking_enabled"
        const val KEY_DISABLE_LIBRARY = "disable_library"
        const val KEY_VISITOR_IDENTITY_KEY = "visitor_identity_key"
        const val KEY_BARRIERS = "barriers"
        const val KEY_TRANSFORMATIONS = "transformations"

        val DEFAULT_LOG_LEVEL = LogLevel.ERROR
        const val DEFAULT_BATCH_SIZE = 1
        const val DEFAULT_MAX_QUEUE_SIZE = 100
        const val DEFAULT_EXPIRATION_DAYS = 1
        const val DEFAULT_BATTERY_SAVER = false
        const val DEFAULT_WIFI_ONLY = false
        const val DEFAULT_REFRESH_INTERVAL_MINUTES = 15
        const val DEFAULT_DEEPLINK_TRACKING_ENABLED = true
        const val DEFAULT_DISABLE_LIBRARY = false

        const val MAX_BATCH_SIZE = 10

        fun fromDataObject(settings: DataObject): CoreSettingsImpl {
            val dataSource = settings.getString(KEY_DATA_SOURCE)
            val visitorIdentityKey = settings.getString(KEY_VISITOR_IDENTITY_KEY)

            val logs = settings.get(KEY_LOG_LEVEL, LogLevel.Converter) ?: DEFAULT_LOG_LEVEL
            val batchSize =
                settings.getInt(KEY_BATCH_SIZE)?.coerceAtMost(MAX_BATCH_SIZE) ?: DEFAULT_BATCH_SIZE
            val maxQueueSize = settings.getInt(KEY_MAX_QUEUE_SIZE) ?: DEFAULT_MAX_QUEUE_SIZE
            val expiration =
                settings.getInt(KEY_EXPIRATION)?.seconds ?: DEFAULT_EXPIRATION_DAYS.days
            val batterySaver = settings.getBoolean(KEY_BATTERY_SAVER) ?: DEFAULT_BATTERY_SAVER
            val wifiOnly = settings.getBoolean(KEY_WIFI_ONLY) ?: DEFAULT_WIFI_ONLY
            val interval = settings.getInt(KEY_REFRESH_INTERVAL)?.seconds
                ?: DEFAULT_REFRESH_INTERVAL_MINUTES.minutes
            val deepLinkTrackingEnabled = settings.getBoolean(KEY_DEEPLINK_TRACKING_ENABLED)
                ?: DEFAULT_DEEPLINK_TRACKING_ENABLED
            val disableLibrary = settings.getBoolean(KEY_DISABLE_LIBRARY) ?: DEFAULT_DISABLE_LIBRARY

            val barriers = settings.getDataList(KEY_BARRIERS)
                ?.mapNotNull(Converters.ScopedBarrierConverter::convert)
                ?.toSet() ?: emptySet()

            val transformations = settings.getDataList(KEY_TRANSFORMATIONS)
                ?.mapNotNull(Converters.ScopedTransformationConverter::convert)
                ?.toSet() ?: emptySet()

            return CoreSettingsImpl(
                logLevel = logs,
                dataSource = dataSource,
                batchSize = batchSize,
                maxQueueSize = maxQueueSize,
                expiration = expiration,
                batterySaver = batterySaver,
                wifiOnly = wifiOnly,
                refreshInterval = interval,
                deepLinkTrackingEnabled = deepLinkTrackingEnabled,
                disableLibrary = disableLibrary,
                visitorIdentityKey = visitorIdentityKey,
                barriers = barriers,
                transformations = transformations
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CoreSettingsImpl

        if (logLevel != other.logLevel) return false
        if (dataSource != other.dataSource) return false
        if (batchSize != other.batchSize) return false
        if (maxQueueSize != other.maxQueueSize) return false
        if (expiration != other.expiration) return false
        if (batterySaver != other.batterySaver) return false
        if (wifiOnly != other.wifiOnly) return false
        if (refreshInterval != other.refreshInterval) return false
        if (deepLinkTrackingEnabled != other.deepLinkTrackingEnabled) return false
        if (disableLibrary != other.disableLibrary) return false
        if (visitorIdentityKey != other.visitorIdentityKey) return false
        if (barriers != other.barriers) return false
        if (transformations != other.transformations) return false

        return true
    }

    override fun hashCode(): Int {
        var result = logLevel.hashCode()
        result = 31 * result + (dataSource?.hashCode() ?: 0)
        result = 31 * result + batchSize
        result = 31 * result + maxQueueSize
        result = 31 * result + expiration.hashCode()
        result = 31 * result + batterySaver.hashCode()
        result = 31 * result + wifiOnly.hashCode()
        result = 31 * result + refreshInterval.hashCode()
        result = 31 * result + deepLinkTrackingEnabled.hashCode()
        result = 31 * result + disableLibrary.hashCode()
        result = 31 * result + (visitorIdentityKey?.hashCode() ?: 0)
        result = 31 * result + barriers.hashCode()
        result = 31 * result + transformations.hashCode()
        return result
    }
}