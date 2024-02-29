package com.tealium.core.internal

import android.util.Log
import com.tealium.core.BuildConfig
import com.tealium.core.LogLevel
import com.tealium.core.api.logger.LogHandler
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.logger.Logs
import com.tealium.core.internal.observables.Observable

/**
 * The [LoggerImpl] class is responsible for managing logging at various log levels,
 * such as TRACE, DEBUG, INFO, WARN, and ERROR. It allows for customization of the
 * minimum log level required for a message to be logged.
 *
 * @property logHandler The log handler responsible for recording log messages.
 * @property minLogLevel The minimum log level required for a log message to be recorded.
 */
class LoggerImpl(
    private val logHandler: LogHandler,
    private var minLogLevel: LogLevel = LogLevel.DEBUG,
    onSdkSettingsUpdated: Observable<SdkSettings>,
) : Logger {

    init {
        onSdkSettingsUpdated.subscribe(::onSettingsUpdated)
    }

    /**
     * Determines if a log message should be logged based on the minimum log level.
     *
     * @param logLevel The log level of the message to be logged.
     * @return true if the message should be logged; otherwise, false.
     */
    internal fun shouldLog(logLevel: LogLevel): Boolean {
        return logLevel.level >= minLogLevel.level
    }

    private val _trace: Logs by lazy {
        LogsImpl(LogLevel.TRACE, ::shouldLog, logHandler)
    }

    private val _debug: Logs by lazy {
        LogsImpl(LogLevel.DEBUG, ::shouldLog, logHandler)
    }

    private val _info: Logs by lazy {
        LogsImpl(LogLevel.INFO, ::shouldLog, logHandler)
    }

    private val _warn: Logs by lazy {
        LogsImpl(LogLevel.WARN, ::shouldLog, logHandler)
    }

    private val _error: Logs by lazy {
        LogsImpl(LogLevel.ERROR, ::shouldLog, logHandler)
    }

    override val trace: Logs?
        get() = if (shouldLog(LogLevel.TRACE)) _trace else null

    override val debug: Logs?
        get() = if (shouldLog(LogLevel.DEBUG)) _debug else null

    override val info: Logs?
        get() = if (shouldLog(LogLevel.INFO)) _info else null

    override val warn: Logs?
        get() = if (shouldLog(LogLevel.WARN)) _warn else null

    override val error: Logs?
        get() = if (shouldLog(LogLevel.ERROR)) _error else null

    internal fun onSettingsUpdated(settings: SdkSettings) {
        val coreSettings = settings.coreSettings
        if (coreSettings.logLevel != minLogLevel) {
            minLogLevel = coreSettings.logLevel
        }
    }

    private class LogsImpl(
        private val logLevel: LogLevel,
        private val shouldLog: (LogLevel) -> Boolean,
        private val logHandler: LogHandler,
    ) : Logs {
        override fun log(category: String, message: String) {
            if (shouldLog(logLevel)) {
                logHandler.log(category, message, logLevel)
            }
        }
    }

    object ConsoleLogHandler : LogHandler {
        override fun log(category: String, message: String, logLevel: LogLevel) {
            val console: ((String, String) -> Unit)? = when (logLevel) {
                LogLevel.TRACE -> Log::v
                LogLevel.DEBUG -> Log::d
                LogLevel.INFO -> Log::i
                LogLevel.WARN -> Log::w
                LogLevel.ERROR -> Log::e
                LogLevel.SILENT -> null
            }
            console?.invoke(BuildConfig.TAG, "[$category] - $message")
        }
    }
}