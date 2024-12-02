package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.api.Tealium
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.DeeplinkManager
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.modules.TealiumContext

class DeepLinkManagerWrapper(
    private val moduleProxy: ModuleProxy<DeeplinkManagerImpl>
): DeeplinkManager {
    constructor(
        tealium: Tealium
    ) : this(tealium.createModuleProxy(DeeplinkManagerImpl::class.java))

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

    override val id: String
        get() = moduleName
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    companion object: ModuleFactory {
        private const val moduleName = "DeepLinkManager"

        override val id: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: DataObject): Module? {
            return DeeplinkManagerImpl()
        }
    }
}
