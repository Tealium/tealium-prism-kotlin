package com.tealium.tests.common

import android.app.Application
import com.tealium.core.api.misc.Environment
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.modules.ModuleFactory
import java.util.EventListener

fun getDefaultConfig(
    app: Application,
    accountName: String = "test",
    profileName: String = "test",
    environment: Environment = Environment.DEV,
    modules: List<ModuleFactory> = listOf(),
    events: List<EventListener> = listOf()
): TealiumConfig {
    return TealiumConfig(
        application = app,
        accountName = accountName,
        profileName = profileName,
        environment = environment,
        modules = modules,
        events = events
    )
}