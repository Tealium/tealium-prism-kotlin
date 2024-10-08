package com.tealium.core.internal.modules

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.TraceManager

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

    override val id: String
        get() = moduleName
    override val version: String
        get() = ""

    companion object : ModuleFactory {
        private const val moduleName = "TraceManager"

        override val id: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: DataObject): Module? {
            return TraceManagerImpl()
        }
    }
}
