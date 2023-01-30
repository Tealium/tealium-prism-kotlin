package com.tealium.core.internal

import com.tealium.core.TealiumContext
import com.tealium.core.api.*

class CollectDispatcher(
    private val tealiumContext: TealiumContext,
    private var collectDispatcherSettings: CollectDispatcherSettings
) : Dispatcher, Module {

    override val name: String
        get() = NAME
    override val version: String
        get() = VERSION

    override fun dispatch(dispatches: List<Dispatch>) {
        TODO("Not yet implemented")
    }

    override fun updateSettings(coreSettings: CoreSettings, moduleSettings: ModuleSettings) {
        collectDispatcherSettings = CollectDispatcherSettings.fromModuleSettings(moduleSettings)
    }

    companion object Factory : ModuleFactory {
        const val NAME = "CollectDispatcher"
        const val VERSION = "1.0.0"

        override val name: String
            get() = NAME

        override fun create(context: TealiumContext, settings: ModuleSettings): Module {
            return CollectDispatcher(context, CollectDispatcherSettings.fromModuleSettings(settings))
        }
    }
}

class CollectDispatcherSettings(val overrideCollectUrl: String = DEFAULT_COLLECT_URL) {

    companion object {
        const val DEFAULT_COLLECT_URL = "https://tealium.com"
        const val OVERRIDE_COLLECT_URL = "overrideCollectUrl"

        fun fromModuleSettings(settings: ModuleSettings): CollectDispatcherSettings {
            return CollectDispatcherSettings(
                settings.settings[OVERRIDE_COLLECT_URL] as? String ?: DEFAULT_COLLECT_URL
            )
        }
    }
}