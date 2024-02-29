package com.tealium.core.internal.settings

import com.tealium.core.LogLevel
import com.tealium.core.api.settings.ModuleSettings

data class CoreSettings(
    val logLevel: LogLevel = LogLevel.DEBUG,
    val dataSource: String? = null,
    val batchSize: Int = 1,
    val maxQueueSize: Int = 100,
    val expiration: Int = 86400,
    val batterySaver: Boolean = false,
    val wifiOnly: Boolean = false,
    val refreshInterval: Int = 900,
    val deepLinkTrackingEnabled: Boolean = true,
    val disableLibrary: Boolean = false
) {

    companion object {
        const val moduleName = "core"
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

        private const val REGEX_TIME_AMOUNT = "\\d+"
        private const val REGEX_TIME_UNIT = "[hmds]$"

        const val maxBatchSize = 10

        fun fromModuleSettings(settings: ModuleSettings?): CoreSettings? {
            val logs = settings?.bundle?.get(KEY_LOG_LEVEL, LogLevel.Deserializer)
            val dataSource = settings?.bundle?.getString(KEY_DATA_SOURCE)
            var batchSize = settings?.bundle?.getInt(KEY_BATCH_SIZE)
            batchSize = if (batchSize != null && batchSize > maxBatchSize) maxBatchSize else batchSize
            val maxQueueSize = settings?.bundle?.getInt(KEY_MAX_QUEUE_SIZE)

            val expiration = settings?.bundle?.getString(KEY_EXPIRATION)?.let {
                timeConverter(it)
            }
            val batterySaver = settings?.bundle?.getBoolean(KEY_BATTERY_SAVER)
            val wifiOnly = settings?.bundle?.getBoolean(KEY_WIFI_ONLY)
            val interval = settings?.bundle?.getString(KEY_REFRESH_INTERVAL)?.let {
                timeConverter(it)
            }
            val deepLinkTrackingEnabled = settings?.bundle?.getBoolean(KEY_DEEPLINK_TRACKING_ENABLED)
            val disableLibrary = settings?.bundle?.getBoolean(KEY_DISABLE_LIBRARY)

            return CoreSettings(
                logLevel = logs ?: LogLevel.ERROR,
                dataSource = dataSource,
                batchSize = batchSize ?: 1,
                maxQueueSize = maxQueueSize ?: 100,
                expiration = expiration ?: 86400,
                batterySaver = batterySaver ?: false,
                wifiOnly = wifiOnly ?: false,
                refreshInterval = interval ?: 900,
                deepLinkTrackingEnabled = deepLinkTrackingEnabled ?: true, // TODO
                disableLibrary = disableLibrary ?: false,
            )
        }

        fun timeConverter(time: String): Int {
            val amount = Regex(REGEX_TIME_AMOUNT).find(time)?.let { amount ->
                val unit = Regex(REGEX_TIME_UNIT)
                unit.find(time)?.let {
                    amount.value.toInt() * toSeconds(it.value)
                }
            }
            return amount ?: -1
        }

        private fun toSeconds(unit: String): Int {
            return when (unit) {
                "d" -> 86400
                "h" -> 3600
                "m" -> 60
                "s" -> 1
                else -> 60
            }
        }
    }
}