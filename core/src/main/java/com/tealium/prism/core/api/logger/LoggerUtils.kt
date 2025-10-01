package com.tealium.prism.core.api.logger

/**
 * Inlines a check for whether the [Logger] is currently logging at [LogLevel.TRACE], before
 * evaluating the [message], whilst avoiding an unnecessary anonymous class implementation.
 *
 * This is equivalent to the following code.
 * ```kotlin
 * if (logger.shouldLog(LogLevel.TRACE) {
 *    logger.trace(category, "some message")
 * }
 * ```
 * It is therefore not recommended for use before the initial [LogLevel] has been set during sdk initialization
 */
inline fun Logger.logIfTraceEnabled(category: String, message: () -> String) {
    if (isTraceLogging) {
        trace(category, message())
    }
}

/**
 * Inlines a check for whether the [Logger] is currently logging at [LogLevel.DEBUG], before
 * evaluating the [message], whilst avoiding an unnecessary anonymous class implementation.
 *
 * This is equivalent to the following code.
 * ```kotlin
 * if (logger.shouldLog(LogLevel.DEBUG) {
 *    logger.debug(category, "some message")
 * }
 * ```
 * It is therefore not recommended for use before the initial [LogLevel] has been set during sdk initialization
 */
inline fun Logger.logIfDebugEnabled(category: String, message: () -> String) {
    if (isDebugLogging) {
        debug(category, message())
    }
}

/**
 * Inlines a check for whether the [Logger] is currently logging at [LogLevel.INFO], before
 * evaluating the [message], whilst avoiding an unnecessary anonymous class implementation.
 *
 * This is equivalent to the following code.
 * ```kotlin
 * if (logger.shouldLog(LogLevel.INFO) {
 *    logger.info(category, "some message")
 * }
 * ```
 * It is therefore not recommended for use before the initial [LogLevel] has been set during sdk initialization
 */
inline fun Logger.logIfInfoEnabled(category: String, message: () -> String) {
    if (isInfoLogging) {
        info(category, message())
    }
}

/**
 * Inlines a check for whether the [Logger] is currently logging at [LogLevel.WARN], before
 * evaluating the [message], whilst avoiding an unnecessary anonymous class implementation.
 *
 * This is equivalent to the following code.
 * ```kotlin
 * if (logger.shouldLog(LogLevel.WARN) {
 *    logger.warn(category, "some message")
 * }
 * ```
 * It is therefore not recommended for use before the initial [LogLevel] has been set during sdk initialization
 */
inline fun Logger.logIfWarnEnabled(category: String, message: () -> String) {
    if (isWarnLogging) {
        warn(category, message())
    }
}

/**
 * Inlines a check for whether the [Logger] is currently logging at [LogLevel.ERROR], before
 * evaluating the [message], whilst avoiding an unnecessary anonymous class implementation.
 *
 * This is equivalent to the following code.
 * ```kotlin
 * if (logger.shouldLog(LogLevel.ERROR) {
 *    logger.error(category, "some message")
 * }
 * ```
 * It is therefore not recommended for use before the initial [LogLevel] has been set during sdk initialization
 */
inline fun Logger.logIfErrorEnabled(category: String, message: () -> String) {
    if (isErrorLogging) {
        error(category, message())
    }
}

/**
 * Convenience method to check that the log level is set to trace or higher
 */
inline val Logger.isTraceLogging: Boolean
    get() = shouldLog(LogLevel.TRACE)

/**
 * Convenience method to check that the log level is set to debug or higher
 */
inline val Logger.isDebugLogging: Boolean
    get() = shouldLog(LogLevel.DEBUG)

/**
 * Convenience method to check that the log level is set to info or higher
 */
inline val Logger.isInfoLogging: Boolean
    get() = shouldLog(LogLevel.INFO)

/**
 * Convenience method to check that the log level is set to warn or higher
 */
inline val Logger.isWarnLogging: Boolean
    get() = shouldLog(LogLevel.WARN)

/**
 * Convenience method to check that the log level is set to error or higher
 */
inline val Logger.isErrorLogging: Boolean
    get() = shouldLog(LogLevel.ERROR)