package com.tealium.core.internal

import com.tealium.core.BuildConfig
import com.tealium.core.api.Collector
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Tracker
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.listeners.TrackResultListener
import com.tealium.core.api.logger.Logger
import com.tealium.core.internal.dispatch.DispatchManager
import com.tealium.core.internal.modules.InternalModuleManager

class TrackerImpl(
    private val modules: InternalModuleManager,
    private val dispatchManager: DispatchManager,
    private val logger: Logger
) : Tracker {

    override fun track(dispatch: Dispatch) {
        track(dispatch, null)
    }

    override fun track(dispatch: Dispatch, onComplete: TrackResultListener?) {
        // TODO - this may need to buffer, since the tracker is available during module creation

        // collection
        val builder = modules.getModulesOfType(Collector::class.java)
            .fold(TealiumBundle.Builder()) { builder, collector ->
                builder.putAll(collector.collect())
            }
        dispatch.addAll(builder.getBundle())

        logger.debug?.log(
            BuildConfig.TAG,
            "Dispatch(${dispatch.id.substring(0, 5)}) Ready - ${dispatch.payload()}"
        )

        // Dispatch
        // TODO - this might have been queued/batched.
        dispatchManager.track(dispatch, onComplete)
    }
}