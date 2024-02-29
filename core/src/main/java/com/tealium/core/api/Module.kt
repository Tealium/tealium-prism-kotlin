package com.tealium.core.api

import com.tealium.core.api.settings.ModuleSettings

interface Module {
    val name: String
    val version: String

    fun updateSettings(moduleSettings: ModuleSettings) : Module? {
        return this
    }
}
