package com.tealium.core.internal.logger

import com.tealium.core.api.logger.AlternateLogger
import com.tealium.core.api.logger.LogHandler
import com.tealium.core.api.logger.LogLevel
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.internal.pubsub.subscribeOnce

/**
 * [AlternateLogger] default implementation.
 *
 * Log messages will be buffered until the first emission from [onLogLevel] which is used to signify
 * that an approved log level has been set from settings.
 */
class AlternateLoggerImpl(
    private val logHandler: LogHandler,
    private val onLogLevel: Observable<LogLevel>,
    private var logLevel: LogLevel? = null
) : AlternateLogger {

    init {
        if (logLevel == null) {
            // was set from programmatic, this will never be overridden
            onLogLevel.subscribe {
                logLevel = it
            }
        }
    }

    private fun writeLog(
        level: LogLevel,
        category: String,
        message: String,
        args: Array<out Any>? = null
    ) {
        if (args.isNullOrEmpty()) {
            logHandler.log(category, message, level)
        } else {
            logHandler.log(category, message.format(*args), level)
        }
    }

    private fun enqueueLog(
        level: LogLevel,
        category: String,
        message: String,
        args: Array<out Any>? = null
    ) {
        onLogLevel.subscribeOnce { newLevel ->
            if (shouldLog(level, newLevel)) {
                writeLog(level, category, message, args)
            }
        }
    }

    private fun enqueueLog(
        level: LogLevel,
        category: String,
        message: () -> String,
    ) {
        onLogLevel.subscribeOnce { newLevel ->
            if (shouldLog(level, newLevel)) {
                writeLog(level, category, message.invoke())
            }
        }
    }

    private fun logOrQueue(
        level: LogLevel,
        category: String,
        message: String,
        args: Array<out Any>? = null
    ) {
        logLevel?.let { current ->
            if (shouldLog(level, current)) {
                writeLog(level, category, message, args)
            }
            return
        }

        enqueueLog(level, category, message, args)
    }

    private fun logOrQueue(
        level: LogLevel,
        category: String,
        message: () -> String,
    ) {
        logLevel?.let { current ->
            if (shouldLog(level, current)) {
                writeLog(level, category, message.invoke())
            }
            return
        }

        enqueueLog(level, category, message)
    }

    private fun shouldLog(logLevel: LogLevel, minimum: LogLevel): Boolean =
        logLevel >= minimum

    override fun shouldLog(level: LogLevel): Boolean {
        val currentLogLevel = this.logLevel
        return currentLogLevel == null || shouldLog(level, currentLogLevel)
    }

    override fun trace(category: String, message: String, vararg args: Any) =
        logOrQueue(LogLevel.TRACE, category, message, args)

    override fun trace(category: String, message: () -> String) =
        logOrQueue(LogLevel.TRACE, category, message)

    override fun debug(category: String, message: String, vararg args: Any) =
        logOrQueue(LogLevel.DEBUG, category, message, args)

    override fun debug(category: String, message: () -> String) =
        logOrQueue(LogLevel.DEBUG, category, message)

    override fun info(category: String, message: String, vararg args: Any) =
        logOrQueue(LogLevel.INFO, category, message, args)

    override fun info(category: String, message: () -> String) =
        logOrQueue(LogLevel.INFO, category, message)

    override fun warn(category: String, message: String, vararg args: Any) =
        logOrQueue(LogLevel.WARN, category, message, args)

    override fun warn(category: String, message: () -> String) =
        logOrQueue(LogLevel.WARN, category, message)

    override fun error(category: String, message: String, vararg args: Any) =
        logOrQueue(LogLevel.ERROR, category, message, args)

    override fun error(category: String, message: () -> String) =
        logOrQueue(LogLevel.ERROR, category, message)
}