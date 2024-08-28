package com.tealium.tests.common

import android.app.Application
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.misc.Environment
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.settings.CoreSettingsBuilder

fun getDefaultConfig(
    app: Application,
    accountName: String = "test",
    profileName: String = "test",
    environment: Environment = Environment.DEV,
    modules: List<ModuleFactory> = listOf(),
    coreSettings: ((CoreSettingsBuilder) -> CoreSettingsBuilder)? = null
): TealiumConfig {
    return TealiumConfig(
        application = app,
        accountName = accountName,
        profileName = profileName,
        environment = environment,
        modules = modules,
        enforcingCoreSettings = coreSettings
    )
}