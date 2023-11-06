package com.tealium.core.api.logger

/**
 * Logs contract responsible for logging messages with a specific category.
 */
interface Logs {

    /**
     * Logs a message with the given category.
     *
     * @param category The category or identifier associated with the log message.
     * @param message The log message to be recorded.
     */
    fun log(category: String, message: String)
}