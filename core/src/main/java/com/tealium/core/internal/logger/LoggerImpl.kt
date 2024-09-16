package com.tealium.core.internal.logger

import android.annotation.SuppressLint
import android.util.Log
import com.tealium.core.BuildConfig
import com.tealium.core.api.logger.LogLevel
import com.tealium.core.api.logger.LogHandler
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.logger.Logs
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.internal.settings.SdkSettings

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
        @SuppressLint("AndroidLogUsageIssue")
        override fun log(category: String, message: String, logLevel: LogLevel) {
            when (logLevel) {
                LogLevel.TRACE -> Log.v(BuildConfig.TAG, formatMessage(category, message))
                LogLevel.DEBUG -> Log.d(BuildConfig.TAG, formatMessage(category, message))
                LogLevel.INFO -> Log.i(BuildConfig.TAG, formatMessage(category, message))
                LogLevel.WARN -> Log.w(BuildConfig.TAG, formatMessage(category, message))
                LogLevel.ERROR -> Log.e(BuildConfig.TAG, formatMessage(category, message))
                else -> { }
            }
        }

        private fun formatMessage(category: String, message: String): String =
            "[$category] - $message"
    }
}