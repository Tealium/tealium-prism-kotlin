package com.tealium.core.internal.consent

import com.tealium.core.api.consent.CmpAdapter
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.logger.logIfDebugEnabled
import com.tealium.core.api.logger.logIfErrorEnabled
import com.tealium.core.api.logger.logIfTraceEnabled
import com.tealium.core.api.logger.logIfWarnEnabled
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.modules.Module
import com.tealium.core.api.pubsub.CompositeDisposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.internal.dispatch.QueueManager
import com.tealium.core.internal.logger.LogCategory
import com.tealium.core.internal.logger.logDescriptions
import com.tealium.core.internal.persistence.database.getTimestampMilliseconds
import com.tealium.core.internal.pubsub.DisposableContainer
import com.tealium.core.internal.pubsub.addTo
import com.tealium.core.internal.pubsub.asObservableState
import com.tealium.core.internal.pubsub.filterNotNull
import com.tealium.core.internal.settings.consent.ConsentConfiguration
import com.tealium.core.internal.settings.consent.ConsentSettings

class ConsentIntegrationManager(
    private val modules: ObservableState<List<Module>>,
    private val queueManager: QueueManager,
    private val cmpConfigurationSelector: CmpConfigurationSelector,
    private val logger: Logger
) : ConsentManager {

    private val subscriptions: CompositeDisposable = DisposableContainer()

    private val dispatchers: ObservableState<Set<String>>
        get() = modules.map(::mapDispatcherIdsToSet)
            .withState { mapDispatcherIdsToSet(modules.value) }

    private fun mapDispatcherIdsToSet(modules: List<Module>): Set<String> =
        modules.filterIsInstance<Dispatcher>().map(Dispatcher::id).toSet()

    override val configuration: Observable<ConsentConfiguration?> =
        cmpConfigurationSelector.configuration.asObservableState() // TODO - add `asObservable()`

    override val tealiumPurposeExplicitlyBlocked: Boolean
        get() = cmpConfigurationSelector.consentInspector.value?.tealiumExplicitlyBlocked()
            ?: false

    init {
        logConfigurationErrors()

        cmpConfigurationSelector.consentInspector
            .filterNotNull()
            .subscribe(::handleConsentChange)
            .addTo(subscriptions)
    }

    private fun logConfigurationErrors() {
        configuration
            .mapNotNull { configuration ->
                if (configuration == null) {
                    logger.logIfWarnEnabled(LogCategory.CONSENT) {
                        """
                        No ConsentConfiguration selected for CMP: ${cmpConfigurationSelector.cmpAdapter.id}.
                        Make sure you provide a configuration for this specific CMP in the ConsentSettings.
                        """.trimIndent()
                    }
                }
                configuration
            }
            .combine(dispatchers) { configuration, dispatcherIds ->
                dispatcherIds.filter { dispatcherId ->
                    !configuration.hasAtLeastOneRequiredPurposeForDispatcher(dispatcherId)
                }
            }
            .filter { it.isNotEmpty() }
            .distinct()
            .subscribe { misconfiguredDispatchers ->
                logger.logIfErrorEnabled(LogCategory.CONSENT) {
                    "No purpose defined in ConsentConfiguration for dispatchers: $misconfiguredDispatchers.\nThese dispatchers will not fire!"
                }
            }.addTo(subscriptions)
    }

    private fun handleConsentChange(consentInspector: ConsentInspector) {
        val (configuration, decision) = consentInspector
        try {
            if (consentInspector.tealiumExplicitlyBlocked()) return

            val events = queueManager.dequeueDispatches(-1, ID)
                .mapNotNull { dispatch -> dispatch.applyDecision(decision) }
            if (events.isEmpty()) return

            logger.logIfDebugEnabled(LogCategory.CONSENT) {
                "Dispatches enqueued for ${consentInspector.decision.decisionType} decision: ${events.logDescriptions()}"
            }

            enqueueDispatches(events, configuration.refireDispatcherIds)
        } finally {
            queueManager.deleteAllDispatches(ID)
        }
    }

    override fun applyConsent(dispatch: Dispatch): TrackResult {
        val consentInfo = cmpConfigurationSelector.consentInspector.value
            ?: return enqueueForConsentAndAccept(dispatch, "Missing ConsentConfiguration or ConsentDecision, enqueued for Consent.")

        if (consentInfo.tealiumExplicitlyBlocked()) {
            return TrackResult.dropped(dispatch, "Tealium explicitly blocked.")
        }

        if (!consentInfo.tealiumConsented()) {
            return enqueueForConsentAndAccept(dispatch, "Tealium implicitly not consented, enqueued for Consent")
        }

        val consentedDispatch = dispatch.applyDecision(consentInfo.decision)
            ?: return TrackResult.dropped(dispatch, "No unprocessed purposes present.")

        val processors = dispatchers.value.toMutableSet()
        if (consentInfo.allowsRefire()) {
            processors.add(ID)
        }

        queueManager.storeDispatches(listOf(consentedDispatch), processors)
        return TrackResult.accepted(dispatch, "Enqueued for processors: $processors")
    }

    private fun enqueueForConsentAndAccept(dispatch: Dispatch, info: String): TrackResult {
        queueManager.storeDispatches(listOf(dispatch), setOf(ID))
        return TrackResult.accepted(dispatch, info)
    }

    private fun enqueueDispatches(dispatches: List<Dispatch>, refireDispatcherIds: Set<String>) {
        val (refireDispatches, normalDispatches) = dispatches.partition(::shouldRefire)

        enqueueForRefire(refireDispatches, refireDispatcherIds)

        if (normalDispatches.isNotEmpty()) {
            logger.logIfTraceEnabled(LogCategory.CONSENT) {
                "Dispatches enqueued for all dispatchers ${normalDispatches.logDescriptions()}"
            }
            queueManager.storeDispatches(normalDispatches, dispatchers.value)
        }
    }

    private fun enqueueForRefire(dispatches: List<Dispatch>, refireDispatcherIds: Set<String>) {
        if (refireDispatcherIds.isEmpty() || dispatches.isEmpty()) {
            return
        }

        val refireDispatches = dispatches.map { dispatch ->
            Dispatch.create(dispatch.id + "-refire", dispatch.payload(), getTimestampMilliseconds())
        }
        logger.logIfTraceEnabled(LogCategory.CONSENT) {
            "Dispatches enqueued for refire dispatchers ${refireDispatches.logDescriptions()}"
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
            scheduler: Scheduler,
            logger: Logger
        ): ConsentIntegrationManager? {
            if (cmpAdapter == null) return null

            return ConsentIntegrationManager(
                modules,
                queueManager,
                CmpConfigurationSelector(consentSettings, cmpAdapter, scheduler),
                logger
            )
        }


        fun shouldRefire(dispatch: Dispatch): Boolean {
            val processedPurposes =
                dispatch.payload().getDataList(Dispatch.Keys.PROCESSED_PURPOSES)
            return processedPurposes != null && processedPurposes.size != 0
        }

        fun ConsentConfiguration.hasAtLeastOneRequiredPurposeForDispatcher(dispatcherId: String): Boolean =
            purposes.values.find { purpose -> purpose.dispatcherIds.contains(dispatcherId) } != null
    }
}