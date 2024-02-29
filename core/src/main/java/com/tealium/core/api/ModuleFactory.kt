package com.tealium.core.api

import com.tealium.core.TealiumContext
import com.tealium.core.api.settings.ModuleSettings

interface ModuleFactory {
    val name: String
    fun create(context: TealiumContext, settings: ModuleSettings): Module?
}

