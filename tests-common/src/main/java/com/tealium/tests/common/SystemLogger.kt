package com.tealium.tests.common

import com.tealium.core.api.logger.LogHandler
import com.tealium.core.api.logger.LogLevel
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.internal.logger.LoggerImpl

val SystemLogger = LoggerImpl(object : LogHandler {
    override fun log(category: String, message: String, logLevel: LogLevel) {
        println("$category - $message")
    }
}, onLogLevel = Observables.empty(), logLevel = LogLevel.TRACE)