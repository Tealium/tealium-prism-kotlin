package com.tealium.core.internal.consent

import com.tealium.core.BuildConfig
import com.tealium.core.api.consent.ConsentDecision
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import com.tealium.core.api.Module
import com.tealium.core.api.consent.ConsentManagementAdapter
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumList
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.api.listeners.SubscribableState
import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.api.transformations.ScopedTransformation
import com.tealium.core.api.transformations.TransformerRegistry
import com.tealium.core.internal.dispatch.QueueManager
import com.tealium.core.api.transformations.TransformationScope
import com.tealium.core.internal.observables.StateSubject
import com.tealium.core.internal.persistence.getTimestampMilliseconds

class ConsentModule(
    private val modules: SubscribableState<Set<Module>>,
    private val queueManager: QueueManager,
    private val transformerRegistry: TransformerRegistry,
    private val consentManagementAdapter: ConsentManagementAdapter?,
    private val consentSettings: StateSubject<ConsentSettings>,
    private val consentTransformer: ConsentTransformer = ConsentTransformer(consentSettings.asObservableState())
) : ConsentManager {

    override val name: String
        get() = NAME

    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    override val enabled: Boolean
        get() = consentSettings.value.enabled

    private val subscription: Disposable?

    private val consentTransformation = ScopedTransformation(
        VERIFY_CONSENT_TRANSFORMATION_ID,
        consentTransformer.id,
        setOf(TransformationScope.AllDispatchers)
    )
    private val dispatchers: Set<String>
        get() = modules.value.filterIsInstance(Dispatcher::class.java)
            .map(Dispatcher::name)
            .toSet()

    private val refireDispatchers: Set<String>
        get() = dispatchers.filter(consentSettings.value.refireDispatchers::contains)
            .toSet()

    init {
        registerTransformations()

        subscription = consentManagementAdapter?.consentDecision?.subscribe { decision ->
            if (decision == null) return@subscribe

            if (!tealiumConsented(decision.purposes)) {
                if (decision.decisionType == ConsentDecision.DecisionType.Explicit)
                    queueManager.deleteAllDispatches(this.name)
                return@subscribe
            }

            val consentDispatches = queueManager.getQueuedDispatches(-1, this.name)
            enqueueDispatches(consentDispatches.mapNotNull { applyDecision(decision, it) })
            queueManager.deleteAllDispatches(this.name)
        }
    }

    override fun getConsentDecision(): ConsentDecision? {
        return consentManagementAdapter?.consentDecision?.value
    }

    override fun tealiumConsented(purposes: Set<String>): Boolean {
        return purposes.contains("tealium") // TODO - allow configurable purpose name.
    }

    override fun applyConsent(dispatch: Dispatch) {
        if (consentManagementAdapter == null) return // TODO, try and force this as non-optional

        val decision = consentManagementAdapter.consentDecision.value
        if (decision == null || !tealiumConsented(decision.purposes)) {
            if (decision?.decisionType != ConsentDecision.DecisionType.Explicit) {
                queueManager.storeDispatches(listOf(dispatch), setOf(this.name))
            }
            return
        }

        val consentedDispatch = applyDecision(decision, dispatch) ?: return

        val processors = dispatchers.toMutableSet()
        if (refireDispatchers.isNotEmpty() && consentDecisionAllowsRefire(
                decision,
                consentManagementAdapter.getAllPurposes()
            )
        ) {
            processors.add(this.name)
        }
        queueManager.storeDispatches(listOf(consentedDispatch), processors)
    }

    override fun updateSettings(moduleSettings: ModuleSettings): Module? {
        consentSettings.onNext(ConsentSettings.fromBundle(moduleSettings.bundle))

        if (!consentSettings.value.enabled) {
            unregisterTransformations()
            subscription?.dispose()
            return null
        }

        return this
    }

    private fun enqueueDispatches(dispatches: List<Dispatch>) {
        dispatches.forEach { dispatch ->
            if (shouldRefire(dispatch)) {
                enqueueForRefire(dispatch)
            } else {
                queueManager.storeDispatches(listOf(dispatch), dispatchers)
            }
        }
    }

    private fun enqueueForRefire(dispatch: Dispatch) {
        if (refireDispatchers.isEmpty()) {
            return
        }

        Dispatch.create(
            dispatch.id + "-refire",
            dispatch.payload(),
            getTimestampMilliseconds()
        )?.let { refireDispatch ->
            queueManager.storeDispatches(
                listOf(refireDispatch),
                refireDispatchers
            )
        }
    }

    private fun registerTransformations() {
        transformerRegistry.registerTransformer(consentTransformer)
        transformerRegistry.registerScopedTransformation(consentTransformation)
    }

    private fun unregisterTransformations() {
        transformerRegistry.unregisterTransformer(consentTransformer)
        transformerRegistry.unregisterScopedTransformation(consentTransformation)
    }

    companion object {
        const val NAME = "consent"

        const val VERIFY_CONSENT_TRANSFORMATION_ID = "verify_consent"

        fun applyDecision(decision: ConsentDecision, dispatch: Dispatch): Dispatch? {
            val processedPurposes = dispatch.payload()
                .getList(Dispatch.Keys.PURPOSES_WITH_CONSENT_ALL) ?: TealiumList.EMPTY_LIST

            val unprocessedPurposes = decision.purposes.filter { purpose ->
                processedPurposes.find {
                    it.value == purpose
                } == null
            }
            if (unprocessedPurposes.isEmpty()) return null

            dispatch.addAll(TealiumBundle.create {
                put(
                    Dispatch.Keys.PURPOSES_WITH_CONSENT_UNPROCESSED,
                    TealiumList.fromCollection(unprocessedPurposes)
                )
                put(Dispatch.Keys.PURPOSES_WITH_CONSENT_PROCESSED, processedPurposes)
                put(
                    Dispatch.Keys.PURPOSES_WITH_CONSENT_ALL,
                    TealiumList.fromCollection(decision.purposes)
                )
                put(
                    Dispatch.Keys.CONSENT_TYPE,
                    decision.decisionType.javaClass.simpleName.lowercase()
                )
            })

            return dispatch
        }

        fun shouldRefire(dispatch: Dispatch): Boolean {
            return dispatch.payload().get(Dispatch.Keys.PURPOSES_WITH_CONSENT_PROCESSED) != null
        }

        private fun consentDecisionAllowsRefire(
            consentDecision: ConsentDecision,
            purposes: Set<String>,
        ): Boolean {
            return consentDecision.decisionType == ConsentDecision.DecisionType.Implicit
                    && !consentDecision.matchAll(purposes)
        }
    }
}