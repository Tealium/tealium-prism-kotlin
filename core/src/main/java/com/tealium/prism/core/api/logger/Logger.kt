package com.tealium.prism.core.api.logger

/**
 * A central utility class for processing log statements at various log levels.
 *
 * Log messages are not guaranteed to be processed immediately upon calling one of the logging methods,
 * however they will be processed in the order that they are received.
 *
 * It is preferable to avoid unnecessary code execution when writing log statements. For example,
 * try not to pre-calculate complicated log messages _before_ passing them into the log methods, in case
 * the [LogLevel] is not sufficiently set for it to actually be logged. As such there are several methods
 * available to support efficient logging, some of which will delay evaluation of the message:
 *
 * With a simple String input - this is preferred for simple cases where a pre-determined String input is used
 * and no complicated processing would occur outside of the String allocation
 * ```kotlin
 * logger.trace("category", "Simple message")
 * ```
 *
 * With String.format and varargs - this is typically useful for cases where a small number of arguments
 * are to be formatted into the log message, but the calculation of any of the arguments' `toString` method
 * is non-trivial. e.g. formatting a complex object as a JSON string.
 * Therefore, this approach is also not preferred if the args will need to be manipulated in some way
 * before passing to the log method, or where large lists of args make the statement difficult to read.
 * ```kotlin
 * // good
 * logger.trace("category", "Color: %s", "red") // Color: red
 * logger.trace("category", "$%.2f", money) // $10.55
 *
 * // less good
 * logger.trace("category", "JSON: %s", jsonObject.toJsonString())
 * logger.trace("category", "%s, %s, %s, %s, %s, %s, %s,",
 *      arg1, arg2, arg3, arg4, arg5, arg6, arg7,
 * )
 * ```
 *
 * With a message supplier - this is typically useful for cases where it would be more readable to build
 * the message in a block, but the calculation of the message would benefit from being evaluated later.
 * ```kotlin
 * logger.trace("category") {
 *      jsonObject.toJsonString()
 * }
 * ```
 *
 * **Note.** For Kotlin call sites, there are also `inline` extension methods available that will inline
 * a check that the logger is configured with the required level of logging, before calling the respective
 * method on the logger. This allows the caller to sidestep any complicated message processing whilst
 * also avoiding extra anonymous classes. For instance, the following are equivalent:
 * ```kotlin
 * if (logger.shouldLog(LogLevel.TRACE) {
 *      logger.trace("category", jsonObject.toJsonString())
 * }
 *
 * // or inlined:
 * logger.logIfTraceEnabled("category") {
 *      jsonObject.toJsonString()
 * }
 * ```
 * Most Kotlin call-sites should prefer these extension methods for readability and efficiency, but for
 * cases at the very start of SDK initialization, where no [LogLevel] has been set yet, the implicit [shouldLog]
 * check will always return `true` causing the log message to be eagerly evaluated.
 *
 * Java call-sites, where inlining is not available, can choose whichever method is more appropriate/readable.
 *
 * @see logIfTraceEnabled
 * @see logIfDebugEnabled
 * @see logIfInfoEnabled
 * @see logIfWarnEnabled
 * @see logIfErrorEnabled
 */
interface Logger {

    /**
     * Returns whether or not the given [level] should be logged by the currently configured [LogLevel].
     *
     * Note. This method will return `true` when there is not a [LogLevel] set yet. Therefore,
     * calling any of the logging methods ([trace], [debug] etc) will queue the log message until
     * a [LogLevel] has been set, deferring the decision on whether to log, or not, until then.
     *
     * @param level The [LogLevel] to compare against the currently configured [LogLevel]
     */
    fun shouldLog(level: LogLevel) : Boolean

    /**
     * Logs a message at the given level and category.
     *
     * @param level The [LogLevel] that this message should be logged at
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded
     */
    fun log(level: LogLevel, category: String, message: String)

    /**
     * Logs a message at the given level and category by formatting the [message] using [String.format],
     * inserting the [args] into the placeholders in the order given in the [message]
     *
     * @param level The [LogLevel] that this message should be logged at
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded, with placeholders to be replaced by the values
     * given in [args]
     * @param args Variable number of args to insert into the template.
     */
    fun log(level: LogLevel, category: String, message: String, vararg args: Any?)

    /**
     * Logs a message at the given level and category by evaluating the log message from the given [message]
     *
     * This should be used in cases where a log message may require sizeable computation to produce
     * the message, e.g. writing out JSON objects.
     *
     * @param level The [LogLevel] that this message should be logged at
     * @param category The category or identifier associated with the log message.
     * @param message A block of code used to produce the message to be recorded
     */
    fun log(level: LogLevel, category: String, message: () -> String)

    /**
     * Logs a [LogLevel.TRACE] level message with the given category.
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded
     */
    fun trace(category: String, message: String)

    /**
     * Logs a [LogLevel.TRACE] level message with the given category by formatting the [message] using [String.format],
     * inserting the [args] into the placeholders in the order given in the [message]
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded, with placeholders to be replaced by the values
     * given in [args]
     * @param args Variable number of args to insert into the template.
     */
    fun trace(category: String, message: String, vararg args: Any?)

    /**
     * Logs a [LogLevel.TRACE] level message with the given category by evaluating the log message from the given [message]
     *
     * This should be used in cases where a log message may require sizeable computation to produce
     * the message, e.g. writing out JSON objects.
     *
     * For Kotlin call-sites, there is a preferable `inline` alternative to avoid the anonymous class
     * - [logIfTraceEnabled] - which is not suitable for logging prior to the first [LogLevel] being set.
     *
     * @param category The category or identifier associated with the log message.
     * @param message A block of code used to produce the message to be recorded
     */
    fun trace(category: String, message: () -> String)

    /**
     * Logs a [LogLevel.TRACE] level message with the given category.
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded
     */
    fun debug(category: String, message: String)

    /**
     * Logs a [LogLevel.DEBUG] level message with the given category by formatting the [message] using [String.format],
     * inserting the [args] into the placeholders in the order given in the [message]
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded, with placeholders to be replaced by the values
     * given in [args]
     * @param args Variable number of args to insert into the template.
     */
    fun debug(category: String, message: String, vararg args: Any?)

    /**
     * Logs a [LogLevel.DEBUG] level message with the given category by evaluating the log message from the given [message]
     *
     * This should be used in cases where a log message may require sizeable computation to produce
     * the message, e.g. writing out JSON objects.
     *
     * For Kotlin call-sites, there is a preferable `inline` alternative to avoid the anonymous class
     * - [logIfDebugEnabled] - which is not suitable for logging prior to the first [LogLevel] being set.
     *
     * @param category The category or identifier associated with the log message.
     * @param message A block of code used to produce the message to be recorded
     */
    fun debug(category: String, message: () -> String)

    /**
     * Logs a [LogLevel.TRACE] level message with the given category.
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded
     */
    fun info(category: String, message: String)

    /**
     * Logs a [LogLevel.INFO] level message with the given category by formatting the [message] using [String.format],
     * inserting the [args] into the placeholders in the order given in the [message]
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded, with placeholders to be replaced by the values
     * given in [args]
     * @param args Variable number of args to insert into the template.
     */
    fun info(category: String, message: String, vararg args: Any?)

    /**
     * Logs a [LogLevel.INFO] level message with the given category by evaluating the log message from the given [message]
     *
     * This should be used in cases where a log message may require sizeable computation to produce
     * the message, e.g. writing out JSON objects.
     *
     * For Kotlin call-sites, there is a preferable `inline` alternative to avoid the anonymous class
     * - [logIfInfoEnabled] - which is not suitable for logging prior to the first [LogLevel] being set.
     *
     * @param category The category or identifier associated with the log message.
     * @param message A block of code used to produce the message to be recorded
     */
    fun info(category: String, message: () -> String)

    /**
     * Logs a [LogLevel.TRACE] level message with the given category.
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded
     */
    fun warn(category: String, message: String)

    /**
     * Logs a [LogLevel.WARN] level message with the given category by formatting the [message] using [String.format],
     * inserting the [args] into the placeholders in the order given in the [message]
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded, with placeholders to be replaced by the values
     * given in [args]
     * @param args Variable number of args to insert into the template.
     */
    fun warn(category: String, message: String, vararg args: Any?)

    /**
     * Logs a [LogLevel.WARN] level message with the given category by evaluating the log message from the given [message]
     *
     * This should be used in cases where a log message may require sizeable computation to produce
     * the message, e.g. writing out JSON objects.
     *
     * For Kotlin call-sites, there is a preferable `inline` alternative to avoid the anonymous class
     * - [logIfWarnEnabled] - which is not suitable for logging prior to the first [LogLevel] being set.
     *
     * @param category The category or identifier associated with the log message.
     * @param message A block of code used to produce the message to be recorded
     */
    fun warn(category: String, message: () -> String)

    /**
     * Logs a [LogLevel.TRACE] level message with the given category.
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded
     */
    fun error(category: String, message: String)

    /**
     * Logs a [LogLevel.ERROR] level message with the given category by formatting the [message] using [String.format],
     * inserting the [args] into the placeholders in the order given in the [message]
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded, with placeholders to be replaced by the values
     * given in [args]
     * @param args Variable number of args to insert into the template.
     */
    fun error(category: String, message: String, vararg args: Any?)

    /**
     * Logs a [LogLevel.INFO] level message with the given category by evaluating the log message from the given [message]
     *
     * This should be used in cases where a log message may require sizeable computation to produce
     * the message, e.g. writing out JSON objects.
     *
     * For Kotlin call-sites, there is a preferable `inline` alternative to avoid the anonymous class
     * - [logIfErrorEnabled] - which is not suitable for logging prior to the first [LogLevel] being set.
     *
     * @param category The category or identifier associated with the log message.
     * @param message A block of code used to produce the message to be recorded
     */
    fun error(category: String, message: () -> String)
}