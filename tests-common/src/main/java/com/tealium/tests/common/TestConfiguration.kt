package com.tealium.tests.common

import android.app.Application
import com.tealium.core.Environment
import com.tealium.core.TealiumConfig
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.listeners.Listener

fun getDefaultConfig(
    app: Application,
    accountName: String = "test",
    profileName: String = "test",
    environment: Environment = Environment.DEV,
    modules: List<ModuleFactory> = listOf(),
    events: List<Listener> = listOf()
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