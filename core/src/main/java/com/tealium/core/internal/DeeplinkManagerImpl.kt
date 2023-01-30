package com.tealium.core.internal

import com.tealium.core.TealiumContext
import com.tealium.core.api.DeeplinkManager
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleSettings
import com.tealium.core.internal.modules.ModuleManagerImpl
import java.lang.ref.WeakReference

class DeepLinkManagerWrapper(
    private val moduleManager: WeakReference<ModuleManagerImpl>
): DeeplinkManager {
    private val delegate: DeeplinkManager?
        get() = moduleManager.get()?.getModuleOfType(DeeplinkManager::class.java)

    override fun handle(link: String) {
        delegate?.handle(link)
    }

    override fun handle(link: String, referrer: String) {
        delegate?.handle(link, referrer)
    }
}

class DeeplinkManagerImpl: DeeplinkManager, Module {
    override fun handle(link: String) {
        TODO("Not yet implemented")
    }

    override fun handle(link: String, referrer: String) {
        TODO("Not yet implemented")
    }

    override val name: String
        get() = moduleName
    override val version: String
        get() = "" // TODO

    companion object: ModuleFactory {
        private const val moduleName = "DeepLinkManager"

        override val name: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: ModuleSettings): Module {
            return DeeplinkManagerImpl()
        }
    }
}
