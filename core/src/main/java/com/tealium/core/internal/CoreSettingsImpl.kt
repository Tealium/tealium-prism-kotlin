package com.tealium.core.internal

import com.tealium.core.Environment
import com.tealium.core.LogLevel
import com.tealium.core.api.CoreSettings

class CoreSettingsImpl(
    override val account: String,
    override val profile: String,
    override val environment: Environment,
    override val dataSource: String? = null,
    override val logLevel: LogLevel = LogLevel.ERROR
): CoreSettings