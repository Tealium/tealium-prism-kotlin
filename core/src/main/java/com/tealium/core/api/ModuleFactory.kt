package com.tealium.core.api

import com.tealium.core.TealiumContext

interface ModuleFactory {
    val name: String
    fun create(context: TealiumContext, settings: ModuleSettings): Module?
}

