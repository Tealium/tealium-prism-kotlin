package com.tealium.core.internal.modules

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.TimedEventsManager

class TimedEventsManagerWrapper(
    private val moduleProxy: ModuleProxy<TimedEventsManagerImpl>
): TimedEventsManager {
    constructor(
        moduleManager: ModuleManager
    ) : this(ModuleProxy(TimedEventsManagerImpl::class.java, moduleManager))



    override fun start(name: String, data: Map<String, Any>?) {
        moduleProxy.getModule { timedEvents ->
            timedEvents?.start(name, data)
        }
    }

    override fun stop(name: String) {
        moduleProxy.getModule { timedEvents ->
            timedEvents?.stop(name)
        }
    }

    override fun cancel(name: String) {
        moduleProxy.getModule { timedEvents ->
            timedEvents?.cancel(name)
        }
    }

    override fun cancelAll() {
        moduleProxy.getModule { timedEvents ->
            timedEvents?.cancelAll()
        }
    }
}

class TimedEventsManagerImpl: TimedEventsManager, Module {
    override fun start(name: String, data: Map<String, Any>?) {
//        TODO("Not yet implemented")
    }

    override fun stop(name: String) {
//        TODO("Not yet implemented")
    }

    override fun cancel(name: String) {
//        TODO("Not yet implemented")
    }

    override fun cancelAll() {
//        TODO("Not yet implemented")
    }

    override val id: String
        get() = moduleName
    override val version: String
        get() = "" //TODO

    companion object: ModuleFactory {
        private const val moduleName = "TimedEvents"

        override val id: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: DataObject): Module? {
            return TimedEventsManagerImpl()
        }
    }
}
