package com.tealium.core.api.logger

import com.tealium.core.LogLevel

/**
 * [LogHandler] is responsible for handling and recording log messages.
 */
interface LogHandler {

    /**
     * Logs a message with a specified category.
     *
     * @param category The category or identifier associated with the log message.
     * @param message The log message to be recorded.
     * @param logLevel The log level of the message.
     */
    fun log(category: String, message: String, logLevel: LogLevel)
}