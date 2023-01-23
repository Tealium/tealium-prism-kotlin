package com.tealium.core.internal

import com.tealium.core.api.Module
import com.tealium.core.api.TimedEventsManager
import com.tealium.core.internal.modules.ModuleManagerImpl
import java.lang.ref.WeakReference

class TimedEventsManagerWrapper(
    private val moduleManager: WeakReference<ModuleManagerImpl>
): TimedEventsManager {
    private val delegate: TimedEventsManager?
        get() = moduleManager.get()?.getModuleOfType(TimedEventsManager::class.java)

    override fun start(name: String, data: Map<String, Any>?) {
        delegate?.start(name, data)
    }

    override fun stop(name: String) {
        delegate?.stop(name)
    }

    override fun cancel(name: String) {
        delegate?.cancel(name)
    }

    override fun cancelAll() {
        delegate?.cancelAll()
    }
}

class TimedEventsManagerImpl: TimedEventsManager, Module {
    override fun start(name: String, data: Map<String, Any>?) {
        TODO("Not yet implemented")
    }

    override fun stop(name: String) {
        TODO("Not yet implemented")
    }

    override fun cancel(name: String) {
        TODO("Not yet implemented")
    }

    override fun cancelAll() {
        TODO("Not yet implemented")
    }

    override val name: String
        get() = "TimedEvents"
    override val version: String
        get() = "" //TODO
}
