package com.tealium.prism.core.internal.modules.trace

import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.settings.modules.TraceSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.api.tracking.TrackResultListener
import com.tealium.prism.core.api.tracking.Tracker

class TraceModule(
    private val dataStore: DataStore,
    private val tracker: Tracker
) : Collector {

    fun killVisitorSession(callback: TrackResultListener) {
        val traceId = dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID)
            ?: throw TealiumException("Not in an active Trace")

        val killDispatch = createKillDispatch(traceId)
        tracker.track(killDispatch, DispatchContext.Source.module(this::class.java), callback)
    }

    fun join(id: String) {
        dataStore.edit()
            .put(Dispatch.Keys.TEALIUM_TRACE_ID, id, Expiry.SESSION)
            .commit()
    }

    fun leave() {
        dataStore.edit()
            .remove(Dispatch.Keys.TEALIUM_TRACE_ID)
            .commit()
    }

    override fun collect(dispatchContext: DispatchContext): DataObject {
        val traceId = dataStore.get(Dispatch.Keys.TEALIUM_TRACE_ID)
            ?: return DataObject.EMPTY_OBJECT

        return DataObject.create {
            put(Dispatch.Keys.TEALIUM_TRACE_ID, traceId)
            put(Dispatch.Keys.CP_TRACE_ID, traceId)
        }
    }

    override val id: String = Modules.Types.TRACE
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    private companion object Companion {
        const val KILL_VISITOR_SESSION_EVENT = "kill_visitor_session"
        private fun createKillDispatch(traceId: String): Dispatch {
            return Dispatch.create(
                KILL_VISITOR_SESSION_EVENT,
                dataObject = DataObject.create {
                    put(Dispatch.Keys.EVENT, KILL_VISITOR_SESSION_EVENT)
                    put(Dispatch.Keys.TEALIUM_TRACE_ID, traceId)
                    put(Dispatch.Keys.CP_TRACE_ID, traceId)
                }
            )
        }
    }

    class Factory(
        settings: DataObject? = null
    ) : ModuleFactory {

        private val enforcedSettings: List<DataObject> =
            settings?.let { listOf(it) } ?: emptyList()

        constructor(settingsBuilder: TraceSettingsBuilder) : this(settingsBuilder.build())

        override fun getEnforcedSettings(): List<DataObject> = enforcedSettings

        override val moduleType: String = Modules.Types.TRACE

        override fun create(moduleId: String, context: TealiumContext, configuration: DataObject): Module? {
            val storage = context.storageProvider.getModuleStore(moduleId)
            return TraceModule(storage, context.tracker)
        }
    }
}
