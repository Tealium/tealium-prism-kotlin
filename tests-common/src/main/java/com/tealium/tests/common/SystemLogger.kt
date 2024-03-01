package com.tealium.tests.common

import com.tealium.core.api.logger.Logger
import com.tealium.core.api.logger.Logs

class SystemLogger: Logger {
    private val systemLog: Logs = object : Logs {
        override fun log(category: String, message: String) {
            println("$category - $message")
        }
    }
    override val trace: Logs?
        get() = systemLog
    override val debug: Logs?
        get() = systemLog
    override val info: Logs?
        get() = systemLog
    override val warn: Logs?
        get() = systemLog
    override val error: Logs?
        get() = systemLog
}