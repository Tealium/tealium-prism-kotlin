package com.tealium.prism.core.api.logger

import android.util.Log
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItem
import java.util.Locale

/**
 * The [LogLevel] enum class defines different log levels used for logging messages.
 * Each log level is associated with an integer value that determines its priority.
 *
 * @property level The integer value associated with the log level, used for priority comparison.
 */
enum class LogLevel(val level: Int) : DataItemConvertible {
    TRACE(Log.VERBOSE),
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR),
    SILENT(Integer.MAX_VALUE);

    override fun asDataItem(): DataItem {
        return DataItem.string(this.name.lowercase())
    }

    object Converter : DataItemConverter<LogLevel> {
        override fun convert(dataItem: DataItem): LogLevel {
            return when (dataItem.getString()?.lowercase(Locale.ROOT)) {
                "trace" -> TRACE
                "debug" -> DEBUG
                "info" -> INFO
                "warn" -> WARN
                "error" -> ERROR
                "silent" -> SILENT
                else -> ERROR
            }
        }
    }
}