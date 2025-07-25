package com.tealium.core.internal.settings

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.LogLevel
import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.misc.TimeFrameUtils.days
import com.tealium.core.api.misc.TimeFrameUtils.minutes
import com.tealium.core.api.misc.TimeFrameUtils.seconds
import com.tealium.core.api.settings.CoreSettings

data class CoreSettingsImpl(
    override val logLevel: LogLevel = DEFAULT_LOG_LEVEL,
    override val maxQueueSize: Int = DEFAULT_MAX_QUEUE_SIZE,
    override val expiration: TimeFrame = DEFAULT_EXPIRATION_DAYS.days,
    override val refreshInterval: TimeFrame = DEFAULT_REFRESH_INTERVAL_MINUTES.minutes,
    override val visitorIdentityKey: String? = null,
) : CoreSettings {

    companion object {
        const val MODULE_NAME = "core"
        const val KEY_LOG_LEVEL = "log_level"
        const val KEY_MAX_QUEUE_SIZE = "max_queue_size"
        const val KEY_EXPIRATION = "expiration"
        const val KEY_REFRESH_INTERVAL = "refresh_interval"
        const val KEY_VISITOR_IDENTITY_KEY = "visitor_identity_key"

        val DEFAULT_LOG_LEVEL = LogLevel.ERROR
        const val DEFAULT_MAX_QUEUE_SIZE = 100
        const val DEFAULT_EXPIRATION_DAYS = 1
        const val DEFAULT_REFRESH_INTERVAL_MINUTES = 15

        fun fromDataObject(settings: DataObject): CoreSettingsImpl {
            val visitorIdentityKey = settings.getString(KEY_VISITOR_IDENTITY_KEY)
            val logs = settings.get(KEY_LOG_LEVEL, LogLevel.Converter)
                ?: DEFAULT_LOG_LEVEL
            val maxQueueSize = settings.getInt(KEY_MAX_QUEUE_SIZE)
                ?: DEFAULT_MAX_QUEUE_SIZE
            val expiration = settings.getInt(KEY_EXPIRATION)?.seconds
                ?: DEFAULT_EXPIRATION_DAYS.days
            val interval = settings.getInt(KEY_REFRESH_INTERVAL)?.seconds
                ?: DEFAULT_REFRESH_INTERVAL_MINUTES.minutes

            return CoreSettingsImpl(
                logLevel = logs,
                maxQueueSize = maxQueueSize,
                expiration = expiration,
                refreshInterval = interval,
                visitorIdentityKey = visitorIdentityKey,
            )
        }
    }
}