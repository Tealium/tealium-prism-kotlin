package com.tealium.core.internal

import com.tealium.core.TealiumContext
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleSettings
import com.tealium.core.api.TraceManager
import com.tealium.core.internal.modules.ModuleManagerImpl
import java.lang.ref.WeakReference

class TraceManagerWrapper(
    private val moduleManager: WeakReference<ModuleManagerImpl>
): TraceManager {
    private val delegate: TraceManager?
        get() = moduleManager.get()?.getModuleOfType(TraceManager::class.java)

    override fun killVisitorSession() {
        delegate?.killVisitorSession()
    }

    override fun join(id: String) {
        delegate?.join(id)
    }

    override fun leave() {
        delegate?.leave()
    }
}

class TraceManagerImpl: TraceManager, Module {

    override fun killVisitorSession() {
        TODO("Not yet implemented")
    }

    override fun join(id: String) {
        TODO("Not yet implemented")
    }

    override fun leave() {
        TODO("Not yet implemented")
    }

    override val name: String
        get() = moduleName
    override val version: String
        get() = ""

    companion object: ModuleFactory {
        private const val moduleName = "TraceManager"

        override val name: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: ModuleSettings): Module {
            return TraceManagerImpl()
        }
    }
}
