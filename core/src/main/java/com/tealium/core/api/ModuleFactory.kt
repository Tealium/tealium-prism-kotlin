package com.tealium.core.api

import com.tealium.core.TealiumContext

interface ModuleFactory {
    val name: String
    fun create(context: TealiumContext, settings: ModuleSettings): Module
}

class Example: Module, Collector {
    override val name: String
        get() = NAME
    override val version: String
        get() = "1.0.0"

    override fun collect(): Map<String, Any> {
        return mapOf(
            "module_name" to name,
            "module_version" to version
        )
    }

    companion object Factory: ModuleFactory {
        private const val NAME = "Example"
        override val name: String
            get() = NAME

        override fun create(context: TealiumContext, settings: ModuleSettings): Module {
            return Example()
        }
    }
}