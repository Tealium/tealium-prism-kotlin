package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.DeeplinkManager
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.settings.ModuleSettings

class DeepLinkManagerWrapper(
    private val moduleProxy: ModuleProxy<DeeplinkManagerImpl>
): DeeplinkManager {
    constructor(
        moduleManager: ModuleManager
    ) : this(ModuleProxy(DeeplinkManagerImpl::class.java, moduleManager))

    override fun handle(link: String) {
        moduleProxy.getModule { deepLink ->
            deepLink?.handle(link)
        }
    }

    override fun handle(link: String, referrer: String) {
        moduleProxy.getModule { deepLink ->
            deepLink?.handle(link, referrer)
        }
    }
}

class DeeplinkManagerImpl: DeeplinkManager, Module {
    override fun handle(link: String) {
//        TODO("Not yet implemented")
    }

    override fun handle(link: String, referrer: String) {
//        TODO("Not yet implemented")
    }

    override val name: String
        get() = moduleName
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    companion object: ModuleFactory {
        private const val moduleName = "DeepLinkManager"

        override val name: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: ModuleSettings): Module? {
            return DeeplinkManagerImpl()
        }
    }
}
