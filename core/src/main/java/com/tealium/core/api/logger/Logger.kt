package com.tealium.core.api.logger

/**
 * The [Logger] is responsible for managing Logs at different log levels.
 * It provides access to difference log level instances.
 */
interface Logger {
    val trace: Logs?
    val debug: Logs?
    val info: Logs?
    val warn: Logs?
    val error: Logs?
}