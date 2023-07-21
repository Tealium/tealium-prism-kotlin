package com.tealium.core.internal.modules

import com.tealium.core.TealiumContext
import com.tealium.core.api.*
import com.tealium.core.api.data.bundle.TealiumBundle

class Example(
    private val dataStore: DataStore
): Module, Collector {
    override val name: String
        get() = NAME
    override val version: String
        get() = "1.0.0"

    override fun collect(): TealiumBundle {
        return TealiumBundle.create {
            put("module_name", name)
            put("module_version", version)
        }
    }

    companion object Factory: ModuleFactory {
        private const val NAME = "Example"
        override val name: String
            get() = NAME

        override fun create(context: TealiumContext, settings: ModuleSettings): Module {
            val dataStore = context.storageProvider.getDataStore(this)

            return Example(dataStore)
        }
    }
}