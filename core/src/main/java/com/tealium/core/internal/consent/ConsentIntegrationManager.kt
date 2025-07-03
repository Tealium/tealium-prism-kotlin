package com.tealium.core.internal.consent

import com.tealium.core.api.consent.CmpAdapter
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.modules.Module
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.internal.dispatch.QueueManager
import com.tealium.core.internal.persistence.database.getTimestampMilliseconds
import com.tealium.core.internal.pubsub.asObservableState
import com.tealium.core.internal.pubsub.filterNotNull
import com.tealium.core.internal.settings.consent.ConsentConfiguration
import com.tealium.core.internal.settings.consent.ConsentSettings

class ConsentIntegrationManager(
    private val modules: ObservableState<List<Module>>,
    private val queueManager: QueueManager,
    private val cmpConfigurationSelector: CmpConfigurationSelector,
) : ConsentManager {

    private val subscription: Disposable?

    private val dispatchers: Set<String>
        get() = modules.value.filterIsInstance<Dispatcher>()
            .map(Dispatcher::id)
            .toSet()

    override val configuration: Observable<ConsentConfiguration?> =
        cmpConfigurationSelector.configuration.asObservableState() // TODO - add `asObservable()`

    override val tealiumPurposeExplicitlyBlocked: Boolean
        get() = cmpConfigurationSelector.consentInspector.value?.tealiumExplicitlyBlocked()
            ?: false

    init {
        subscription = cmpConfigurationSelector.consentInspector.filterNotNull()
            .subscribe(::handleConsentChange)
    }

    private fun handleConsentChange(consentInspector: ConsentInspector) {
        val (configuration, decision) = consentInspector
        try {
            if (consentInspector.tealiumExplicitlyBlocked()) return

            val events = queueManager.getQueuedDispatches(-1, ID)
                .mapNotNull { dispatch -> dispatch.applyDecision(decision) }
            if (events.isEmpty()) return

            enqueueDispatches(events, configuration.refireDispatcherIds)
        } finally {
            queueManager.deleteAllDispatches(ID)
        }
    }

    override fun applyConsent(dispatch: Dispatch): TrackResult {
        val consentInfo = cmpConfigurationSelector.consentInspector.value
            ?: return enqueueForConsentAndAccept(dispatch)

        if (consentInfo.tealiumExplicitlyBlocked()) {
            return TrackResult.Dropped(dispatch)
        }

        if (!consentInfo.tealiumConsented()) {
            return enqueueForConsentAndAccept(dispatch)
        }

        val consentedDispatch = dispatch.applyDecision(consentInfo.decision)
            ?: return TrackResult.Dropped(dispatch)

        val processors = dispatchers.toMutableSet()
        if (consentInfo.allowsRefire()) {
            processors.add(ID)
        }

        queueManager.storeDispatches(listOf(consentedDispatch), processors)
        return TrackResult.Accepted(dispatch)
    }

    private fun enqueueForConsentAndAccept(dispatch: Dispatch): TrackResult {
        queueManager.storeDispatches(listOf(dispatch), setOf(ID))
        return TrackResult.Accepted(dispatch)
    }

    private fun enqueueDispatches(dispatches: List<Dispatch>, refireDispatcherIds: Set<String>) {
        val (refireDispatches, normalDispatches) = dispatches.partition(::shouldRefire)

        enqueueForRefire(refireDispatches, refireDispatcherIds)

        if (normalDispatches.isNotEmpty()) {
            queueManager.storeDispatches(normalDispatches, dispatchers)
        }
    }

    private fun enqueueForRefire(dispatches: List<Dispatch>, refireDispatcherIds: Set<String>) {
        if (refireDispatcherIds.isEmpty() || dispatches.isEmpty()) {
            return
        }

        val refireDispatches = dispatches.map { dispatch ->
            Dispatch.create(dispatch.id + "-refire", dispatch.payload(), getTimestampMilliseconds())
        }
        queueManager.storeDispatches(
            refireDispatches,
            refireDispatcherIds
        )
    }

    companion object {
        const val ID = "consent"

        fun create(
            modules: ObservableState<List<Module>>,
            queueManager: QueueManager,
            cmpAdapter: CmpAdapter?,
            consentSettings: ObservableState<ConsentSettings?>,
            scheduler: Scheduler
        ): ConsentIntegrationManager? {
            if (cmpAdapter == null) return null

            return ConsentIntegrationManager(
                modules,
                queueManager,
                CmpConfigurationSelector(consentSettings, cmpAdapter, scheduler)
            )
        }


        fun shouldRefire(dispatch: Dispatch): Boolean {
            val processedPurposes =
                dispatch.payload().getDataList(Dispatch.Keys.PURPOSES_WITH_CONSENT_PROCESSED)
            return processedPurposes != null && processedPurposes.size != 0
        }
    }
}