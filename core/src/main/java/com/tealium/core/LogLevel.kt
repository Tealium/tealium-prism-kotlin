package com.tealium.core

import android.util.Log
import com.tealium.core.api.data.TealiumDeserializable
import com.tealium.core.api.data.TealiumSerializable
import com.tealium.core.api.data.TealiumValue
import java.util.Locale

/**
 * The [LogLevel] enum class defines different log levels used for logging messages.
 * Each log level is associated with an integer value that determines its priority.
 *
 * @property level The integer value associated with the log level, used for priority comparison.
 */
enum class LogLevel(val level: Int) : TealiumSerializable {
    TRACE(Log.VERBOSE),
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR),
    SILENT(Integer.MAX_VALUE);

    override fun asTealiumValue(): TealiumValue {
        return TealiumValue.string(this.name)
    }

    object Deserializer : TealiumDeserializable<LogLevel> {
        override fun deserialize(value: TealiumValue): LogLevel {
            return when (value.getString()?.lowercase(Locale.ROOT)) {
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