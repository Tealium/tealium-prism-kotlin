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
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.settings.modules.TraceSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.api.tracking.TrackResultListener
import com.tealium.prism.core.api.tracking.Tracker
import com.tealium.prism.core.internal.logger.ErrorEvent
import com.tealium.prism.core.internal.logger.LoggerImpl

class TraceModule(
    private val dataStore: DataStore,
    private val tracker: Tracker,
    private var configuration: TraceModuleConfiguration,
    internal val onErrorEvent: Observable<ErrorEvent>? = null
) : Collector {

    private var disposable: Disposable? = null

    private val traceId: String?
        get() = dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID)

    private val isTrackErrorsEnabled: Boolean
        get() = configuration.trackErrors

    private val errorCache = mutableSetOf<String>()

    init {
        updateErrorEventsSubscription()
    }

    fun forceEndOfVisit(callback: TrackResultListener) {
        val traceId = traceId
            ?: throw TealiumException("Not in an active Trace")

        val endVisitDispatch = createEndVisitDispatch(traceId)
        tracker.track(endVisitDispatch, DispatchContext.Source.module(this::class.java), callback)
    }

    fun join(id: String) {
        errorCache.clear()

        dataStore.edit()
            .put(Dispatch.Keys.TEALIUM_TRACE_ID, id, Expiry.SESSION)
            .commit()

        subscribeToErrorEvents()
    }

    fun leave() {
        dataStore.edit()
            .remove(Dispatch.Keys.TEALIUM_TRACE_ID)
            .commit()

        unsubscribeFromErrorEvents()
        errorCache.clear()
    }

    private fun subscribeToErrorEvents() {
        if (onErrorEvent == null || disposable != null) {
            return
        }

        if (isTrackErrorsEnabled) {
            disposable = onErrorEvent.subscribe(::trackErrorEvent)
        }
    }

    private fun unsubscribeFromErrorEvents() {
        disposable?.dispose()
        disposable = null
    }

    private fun updateErrorEventsSubscription() {
        if (isTrackErrorsEnabled && traceId != null) {
            subscribeToErrorEvents()
            return
        }

        unsubscribeFromErrorEvents()
    }

    private fun trackErrorEvent(errorEvent: ErrorEvent) {
        if (errorCache.add(errorEvent.category)) {
            tracker.track(
                createErrorDispatch(errorEvent),
                DispatchContext.Source.module(this::class.java)
            )
        }
    }

    override fun collect(dispatchContext: DispatchContext): DataObject {
        val traceId = traceId
            ?: return DataObject.EMPTY_OBJECT

        return DataObject.create {
            put(Dispatch.Keys.TEALIUM_TRACE_ID, traceId)
            put(Dispatch.Keys.CP_TRACE_ID, traceId)
        }
    }

    override fun updateConfiguration(configuration: DataObject): Module {
        this.configuration = TraceModuleConfiguration.fromDataObject(configuration)
        updateErrorEventsSubscription()
        return this
    }

    override fun onShutdown() {
        unsubscribeFromErrorEvents()
    }

    override val id: String = Modules.Types.TRACE
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    private companion object Companion {
        const val FORCE_END_OF_VISIT_EVENT = "kill_visitor_session"
        const val ERROR_EVENT = "tealium_error"
        const val ERROR_DESCRIPTION_KEY = "error_description"

        private fun createErrorDispatch(errorEvent: ErrorEvent): Dispatch {
            return Dispatch.create(
                ERROR_EVENT,
                dataObject = DataObject.create {
                    put(Dispatch.Keys.EVENT, ERROR_EVENT)
                    put(ERROR_DESCRIPTION_KEY, "${errorEvent.category}: ${errorEvent.description}")
                }
            )
        }

        private fun createEndVisitDispatch(traceId: String): Dispatch {
            return Dispatch.create(
                FORCE_END_OF_VISIT_EVENT,
                dataObject = DataObject.create {
                    put(Dispatch.Keys.EVENT, FORCE_END_OF_VISIT_EVENT)
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

        override fun create(
            moduleId: String,
            context: TealiumContext,
            configuration: DataObject
        ): Module? {
            val storage = context.storageProvider.getModuleStore(moduleId)
            return TraceModule(
                storage,
                context.tracker,
                TraceModuleConfiguration.fromDataObject(configuration),
                (context.logger as? LoggerImpl)?.errors
            )
        }
    }
}
