package com.tealium.core.internal.modules.trace

import com.tealium.core.BuildConfig
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumException
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.api.tracking.Tracker

class TraceManagerModule(
    private val dataStore: DataStore,
    private val tracker: Tracker
) : Collector {

    fun killVisitorSession(callback: TealiumCallback<TealiumResult<Unit>>) {
        val traceId = dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID)
        if (traceId == null) {
            callback.onComplete(TealiumResult.failure(TealiumException("Not in an active Trace")))
            return
        }

        val killDispatch = createKillDispatch(traceId)
        tracker.track(killDispatch, DispatchContext.Source.module(this::class.java)) { _, trackResult ->
            if (trackResult == TrackResult.Dropped) {
                callback.onComplete(TealiumResult.failure(TealiumException("Kill Visitor Session event was dropped.")))
            } else {
                callback.onComplete(TealiumResult.success(Unit))
            }
        }
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
        if (dispatchContext.source.isFromModule(this::class.java))
            return DataObject.EMPTY_OBJECT

        return dataStore.getAll()
    }

    override val id: String
        get() = Factory.id
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    private companion object {
        const val KILL_VISITOR_SESSION_EVENT = "kill_visitor_session"
        private fun createKillDispatch(traceId: String): Dispatch {
            return Dispatch.create(
                KILL_VISITOR_SESSION_EVENT,
                dataObject = DataObject.create {
                    put(Dispatch.Keys.EVENT, KILL_VISITOR_SESSION_EVENT)
                    put(Dispatch.Keys.TEALIUM_TRACE_ID, traceId)
                }
            )
        }
    }

    object Factory : ModuleFactory {
        override val id: String
            get() = "Trace"

        override fun create(context: TealiumContext, settings: DataObject): Module? {
            val storage = context.storageProvider.getModuleStore(this)
            return TraceManagerModule(storage, context.tracker)
        }
    }
}
