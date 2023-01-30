package com.tealium.core.api

interface Module {
    val name: String
    val version: String

    fun updateSettings(coreSettings: CoreSettings, moduleSettings: ModuleSettings) {
        //
    }
}
