package com.tealium.core.internal

import com.tealium.core.TealiumContext
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleManager
import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.api.TraceManager
import com.tealium.core.internal.modules.ModuleProxy

class TraceManagerWrapper(
    private val moduleProxy: ModuleProxy<TraceManagerImpl>
) : TraceManager {

    constructor(
        moduleManager: ModuleManager
    ) : this(ModuleProxy(TraceManagerImpl::class.java, moduleManager))

    override fun killVisitorSession() {
        moduleProxy.getModule { trace ->
            trace?.killVisitorSession()
        }
    }

    override fun join(id: String) {
        moduleProxy.getModule { trace ->
            trace?.join(id)
        }
    }

    override fun leave() {
        moduleProxy.getModule { trace ->
            trace?.leave()
        }
    }
}

class TraceManagerImpl : TraceManager, Module {

    override fun killVisitorSession() {
//        TODO("Not yet implemented")
    }

    override fun join(id: String) {
//        TODO("Not yet implemented")
    }

    override fun leave() {
//        TODO("Not yet implemented")
    }

    override val name: String
        get() = moduleName
    override val version: String
        get() = ""

    companion object : ModuleFactory {
        private const val moduleName = "TraceManager"

        override val name: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: ModuleSettings): Module? {
            return TraceManagerImpl()
        }
    }
}
