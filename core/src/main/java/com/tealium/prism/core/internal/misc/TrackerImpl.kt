package com.tealium.prism.core.internal.misc

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfDebugEnabled
import com.tealium.prism.core.api.logger.logIfTraceEnabled
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.api.tracking.TrackResultListener
import com.tealium.prism.core.api.tracking.Tracker
import com.tealium.prism.core.internal.dispatch.DispatchManager
import com.tealium.prism.core.internal.session.SessionManager
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.pubsub.subscribeOnce
import com.tealium.prism.core.internal.rules.LoadRuleEngine

class TrackerImpl(
    private val modules: Observable<List<Module>>,
    private val dispatchManager: DispatchManager,
    private val loadRuleEngine: LoadRuleEngine,
    private val sessionManager: SessionManager,
    private val logger: Logger
) : Tracker {

    override fun track(dispatch: Dispatch, source: DispatchContext.Source) {
        track(dispatch, source, null)
    }

    override fun track(
        dispatch: Dispatch,
        source: DispatchContext.Source,
        onComplete: TrackResultListener?
    ) {
        if (dispatchManager.tealiumPurposeExplicitlyBlocked) {
            val trackResult = TrackResult.dropped(dispatch, "Tealium consent purpose is explicitly blocked.")
            logger.debug(LogCategory.TEALIUM, trackResult.description)
            onComplete?.onTrackResultReady(trackResult)
            return
        }

        logger.logIfDebugEnabled(LogCategory.TEALIUM) {
            "New tracking event received: ${dispatch.logDescription()}"
        }
        logger.logIfTraceEnabled(LogCategory.TEALIUM) {
            "Event data: ${dispatch.payload()}"
        }
        // collection
        modules.filter { modules -> modules.isNotEmpty() }
            .map { modules -> modules.filterIsInstance<Collector>() }
            .map { collectors -> evaluateLoadRules(collectors, dispatch) }
            .subscribeOnce { collectors ->
                sessionManager.registerDispatch(dispatch)

                val dispatchContext = DispatchContext(source, dispatch.payload())
                val collectedData = collect(collectors, dispatchContext)
                dispatch.addAll(collectedData)

                logger.logIfDebugEnabled(LogCategory.TEALIUM) {
                    "Event: ${dispatch.logDescription()} has been enriched by collectors"
                }
                logger.logIfTraceEnabled(LogCategory.TEALIUM) {
                    "Enriched event data: ${dispatch.payload()}"
                }

                dispatchManager.track(dispatch, onComplete)
            }
    }

    private fun evaluateLoadRules(
        collectors: List<Collector>,
        dispatch: Dispatch
    ): List<Collector> {
        val (passed, failed) = collectors.partition { collector ->
            loadRuleEngine.rulesAllow(collector, dispatch)
        }

        if (failed.isNotEmpty()) {
            logger.logIfTraceEnabled(LogCategory.TEALIUM) {
                "${dispatch.logDescription()}: Collection not allowed for collectors: (${
                    failed.map(Collector::id).joinToString(",")
                })"
            }
        }

        return passed
    }

    private fun collect(
        modules: List<Collector>,
        dispatchContext: DispatchContext
    ) = modules.fold(DataObject.Builder()) { builder, collector ->
        builder.putAll(collector.collect(dispatchContext))
    }.build()
}