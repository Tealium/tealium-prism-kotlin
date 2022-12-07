package com.tealium.core.api

import com.tealium.core.Environment
import com.tealium.core.LogLevel

interface CoreSettings {
    val account: String
    val profile: String
    val environment: Environment
    val dataSource: String?
    val logLevel: LogLevel
}