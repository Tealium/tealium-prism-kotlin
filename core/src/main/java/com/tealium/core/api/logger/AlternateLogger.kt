package com.tealium.core.api.logger

/**
 * A central utility class for processing log statements at various log levels.
 *
 * Log messages are not guaranteed to be processed immediately upon calling one of the logging methods,
 * however they will be processed in the order that they are received.
 */
interface AlternateLogger {

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
     * Logs a message with the given category by formatting the [message] using [String.format],
     * inserting the [args] into the placeholders in the order given in the [message]
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded, with placeholders to be replaced by the values
     * given in [args]
     * @param args Variable number of args to insert into the template.
     */
    fun trace(category: String, message: String, vararg args: Any)

    /**
     * Logs a message with the given category by evaluating the log message from the given [message]
     *
     * This should be used in cases where a log message may require sizeable computation to produce
     * the message, e.g. writing out JSON objects.
     *
     * @param category The category or identifier associated with the log message.
     * @param message A block of code used to produce the message to be recorded
     */
    fun trace(category: String, message: () -> String)

    /**
     * Logs a message with the given category by formatting the [message] using [String.format],
     * inserting the [args] into the placeholders in the order given in the [message]
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded, with placeholders to be replaced by the values
     * given in [args]
     * @param args Variable number of args to insert into the template.
     */
    fun debug(category: String, message: String, vararg args: Any)

    /**
     * Logs a message with the given category by evaluating the log message from the given [message]
     *
     * This should be used in cases where a log message may require sizeable computation to produce
     * the message, e.g. writing out JSON objects.
     *
     * @param category The category or identifier associated with the log message.
     * @param message A block of code used to produce the message to be recorded
     */
    fun debug(category: String, message: () -> String)

    /**
     * Logs a message with the given category by formatting the [message] using [String.format],
     * inserting the [args] into the placeholders in the order given in the [message]
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded, with placeholders to be replaced by the values
     * given in [args]
     * @param args Variable number of args to insert into the template.
     */
    fun info(category: String, message: String, vararg args: Any)

    /**
     * Logs a message with the given category by evaluating the log message from the given [message]
     *
     * This should be used in cases where a log message may require sizeable computation to produce
     * the message, e.g. writing out JSON objects.
     *
     * @param category The category or identifier associated with the log message.
     * @param message A block of code used to produce the message to be recorded
     */
    fun info(category: String, message: () -> String)

    /**
     * Logs a message with the given category by formatting the [message] using [String.format],
     * inserting the [args] into the placeholders in the order given in the [message]
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded, with placeholders to be replaced by the values
     * given in [args]
     * @param args Variable number of args to insert into the template.
     */
    fun warn(category: String, message: String, vararg args: Any)

    /**
     * Logs a message with the given category by evaluating the log message from the given [message]
     *
     * This should be used in cases where a log message may require sizeable computation to produce
     * the message, e.g. writing out JSON objects.
     *
     * @param category The category or identifier associated with the log message.
     * @param message A block of code used to produce the message to be recorded
     */
    fun warn(category: String, message: () -> String)

    /**
     * Logs a message with the given category by formatting the [message] using [String.format],
     * inserting the [args] into the placeholders in the order given in the [message]
     *
     * @param category The category or identifier associated with the log message.
     * @param message The message to be recorded, with placeholders to be replaced by the values
     * given in [args]
     * @param args Variable number of args to insert into the template.
     */
    fun error(category: String, message: String, vararg args: Any)

    /**
     * Logs a message with the given category by evaluating the log message from the given [message]
     *
     * This should be used in cases where a log message may require sizeable computation to produce
     * the message, e.g. writing out JSON objects.
     *
     * @param category The category or identifier associated with the log message.
     * @param message A block of code used to produce the message to be recorded
     */
    fun error(category: String, message: () -> String)
}