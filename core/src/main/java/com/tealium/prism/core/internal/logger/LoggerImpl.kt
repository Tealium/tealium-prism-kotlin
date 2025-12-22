package com.tealium.prism.core.internal.logger

import android.annotation.SuppressLint
import android.util.Log
import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.logger.LogHandler
import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Subject

/**
 * A central utility class for processing log statements at various log levels.
 *
 * Log messages are not guaranteed to be processed immediately upon calling one of the logging methods,
 * however they will be processed in the order that they are received.
 *
 * @param logHandler The [LogHandler] implementation to forward log events to
 * @param onLogLevel An [Observable] stream of chosen [LogLevel]s, which will dictate what log messages are logged
 * @param logLevel An optional, fixed [LogLevel] to use for the lifetime of this object
 */
class LoggerImpl(
    private val scheduler: Scheduler,
    private val logHandler: LogHandler,
    private val onLogLevel: Observable<LogLevel>,
    @Volatile private var logLevel: LogLevel? = null,
    private val _onErrorEvent: Subject<ErrorEvent> = Observables.publishSubject(),
) : Logger {

    private val isErrorTraceSubscribed: Boolean
        get() = _onErrorEvent.count > 0

    internal val errors: Observable<ErrorEvent> = _onErrorEvent.subscribeOn(scheduler)

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
        args: Array<out Any?>? = null
    ) {
        val formattedMessage = if (args.isNullOrEmpty()) message else message.format(*args)

        logHandler.log(category, formattedMessage, level)
    }

    private fun enqueueLog(
        level: LogLevel,
        category: String,
        message: String,
        args: Array<out Any?>? = null
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
        args: Array<out Any?>? = null
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

    private fun emitErrorEvent(category: String, message: String, args: Array<out Any?>? = null) {
        val formattedMessage = if (args.isNullOrEmpty()) message else message.format(*args)
        scheduler.execute {
            _onErrorEvent.onNext(ErrorEvent(category, formattedMessage))
        }
    }

    private fun shouldLog(logLevel: LogLevel, minimum: LogLevel): Boolean =
        logLevel >= minimum

    override fun shouldLog(level: LogLevel): Boolean {
        if (level == LogLevel.ERROR && isErrorTraceSubscribed) {
            return true
        }

        val currentLogLevel = this.logLevel
        return level != LogLevel.SILENT &&
                (currentLogLevel == null || shouldLog(level, currentLogLevel))
    }

    override fun log(level: LogLevel, category: String, message: String) {
        checkErrorSubscription(level, category, message)

        if (level == LogLevel.SILENT) return

        logOrQueue(level, category, message)
    }

    override fun log(level: LogLevel, category: String, message: String, vararg args: Any?) {
        checkErrorSubscription(level, category, message, args)

        if (level == LogLevel.SILENT) return

        logOrQueue(level, category, message, args)
    }

    override fun log(level: LogLevel, category: String, message: () -> String) {
        checkErrorSubscription(level, category, message)

        if (level == LogLevel.SILENT) return

        logOrQueue(level, category, message)
    }

    override fun trace(category: String, message: String) =
        logOrQueue(LogLevel.TRACE, category, message)

    override fun trace(category: String, message: String, vararg args: Any?) =
        logOrQueue(LogLevel.TRACE, category, message, args)

    override fun trace(category: String, message: () -> String) =
        logOrQueue(LogLevel.TRACE, category, message)

    override fun debug(category: String, message: String) =
        logOrQueue(LogLevel.DEBUG, category, message)

    override fun debug(category: String, message: String, vararg args: Any?) =
        logOrQueue(LogLevel.DEBUG, category, message, args)

    override fun debug(category: String, message: () -> String) =
        logOrQueue(LogLevel.DEBUG, category, message)

    override fun info(category: String, message: String) =
        logOrQueue(LogLevel.INFO, category, message)

    override fun info(category: String, message: String, vararg args: Any?) =
        logOrQueue(LogLevel.INFO, category, message, args)

    override fun info(category: String, message: () -> String) =
        logOrQueue(LogLevel.INFO, category, message)

    override fun warn(category: String, message: String) =
        logOrQueue(LogLevel.WARN, category, message)

    override fun warn(category: String, message: String, vararg args: Any?) =
        logOrQueue(LogLevel.WARN, category, message, args)

    override fun warn(category: String, message: () -> String) =
        logOrQueue(LogLevel.WARN, category, message)

    override fun error(category: String, message: String) {
        checkErrorSubscription(LogLevel.ERROR, category, message)

        logOrQueue(LogLevel.ERROR, category, message)
    }

    override fun error(category: String, message: String, vararg args: Any?) {
        checkErrorSubscription(LogLevel.ERROR, category, message, args)

        logOrQueue(LogLevel.ERROR, category, message, args)
    }

    override fun error(category: String, message: () -> String) {
        checkErrorSubscription(LogLevel.ERROR, category, message)

        logOrQueue(LogLevel.ERROR, category, message)
    }

    private fun checkErrorSubscription(
        level: LogLevel,
        category: String,
        message: String,
        args: Array<out Any?>? = null
    ) {
        if (level == LogLevel.ERROR && isErrorTraceSubscribed) {
            emitErrorEvent(category, message, args)
        }
    }

    private fun checkErrorSubscription(level: LogLevel, category: String, message: () -> String) {
        if (level == LogLevel.ERROR && isErrorTraceSubscribed) {
            emitErrorEvent(category, message.invoke())
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
                else -> {}
            }
        }

        private fun formatMessage(category: String, message: String): String =
            "[$category] - $message"
    }
}