package com.tealium.core.internal.modules.consent

import com.tealium.core.BuildConfig
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.consent.ConsentDecision
import com.tealium.core.api.modules.consent.ConsentManagementAdapter
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.api.pubsub.SubscribableState
import com.tealium.core.api.settings.ModuleSettingsBuilder
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.transform.DispatchScope
import com.tealium.core.api.transform.ScopedTransformation
import com.tealium.core.api.transform.TransformationScope
import com.tealium.core.api.transform.Transformer
import com.tealium.core.api.transform.TransformerRegistry
import com.tealium.core.internal.dispatch.QueueManager
import com.tealium.core.internal.persistence.database.getTimestampMilliseconds

class ConsentModule(
    private val modules: SubscribableState<List<Module>>,
    private val queueManager: QueueManager,
    private val transformerRegistry: TransformerRegistry,
    private val consentManagementAdapter: ConsentManagementAdapter,
    private val consentSettings: StateSubject<ConsentSettings>,
) : ConsentManager, Transformer {

    override val id: String
        get() = NAME

    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    private val subscription: Disposable?

    private val consentTransformation = ScopedTransformation(
        VERIFY_CONSENT_TRANSFORMATION_ID,
        id,
        setOf(TransformationScope.AllDispatchers)
    )
    private val dispatchers: Set<String>
        get() = modules.value.filterIsInstance(Dispatcher::class.java)
            .map(Dispatcher::id)
            .toSet()

    private val refireDispatchers: Set<String>
        get() = dispatchers.filter(consentSettings.value.refireDispatchers::contains)
            .toSet()

    init {
        registerTransformations()

        subscription = consentManagementAdapter.consentDecision.subscribe { decision ->
            if (decision == null) return@subscribe

            if (!tealiumConsented(decision.purposes)) {
                if (decision.decisionType == ConsentDecision.DecisionType.Explicit)
                    queueManager.deleteAllDispatches(this.id)
                return@subscribe
            }

            val consentDispatches = queueManager.getQueuedDispatches(-1, this.id)
            enqueueDispatches(consentDispatches.mapNotNull { applyDecision(decision, it) })
            queueManager.deleteAllDispatches(this.id)
        }
    }

    override fun getConsentDecision(): ConsentDecision? {
        return consentManagementAdapter.consentDecision.value
    }

    override fun tealiumConsented(purposes: Set<String>): Boolean {
        return purposes.contains("tealium") // TODO - allow configurable purpose name.
    }

    override fun applyConsent(dispatch: Dispatch) {
        val decision = consentManagementAdapter.consentDecision.value
        if (decision == null || !tealiumConsented(decision.purposes)) {
            if (decision?.decisionType != ConsentDecision.DecisionType.Explicit) {
                queueManager.storeDispatches(listOf(dispatch), setOf(this.id))
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
            processors.add(this.id)
        }
        queueManager.storeDispatches(listOf(consentedDispatch), processors)
    }

    override fun updateSettings(moduleSettings: DataObject): Module? {
        consentSettings.onNext(ConsentSettings.fromDataObject(moduleSettings))
        return this
    }

    override fun onShutdown() {
        unregisterTransformations()
        subscription?.dispose()
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
        transformerRegistry.registerScopedTransformation(consentTransformation)
    }

    private fun unregisterTransformations() {
        transformerRegistry.unregisterScopedTransformation(consentTransformation)
    }

    override fun applyTransformation(
        transformationId: String,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        if (scope is DispatchScope.Dispatcher) {
            val requiredPurposes =
                consentSettings.value.dispatcherPurposes[scope.dispatcher]
                    ?: emptyList()
            if (requiredPurposes.isNotEmpty() && !isConsented(dispatch, requiredPurposes)) {
                completion(null)
                return
            }
        }

        completion(dispatch)
    }

    private fun isConsented(dispatch: Dispatch, requiredPurposes: List<String>): Boolean {
        val consentedPurposes =
            dispatch.payload().getDataList(Dispatch.Keys.PURPOSES_WITH_CONSENT_ALL)?.mapNotNull {
                it.getString()
            } ?: return false

        return consentedPurposes.containsAll(requiredPurposes)
    }

    companion object {
        const val NAME = "Consent"

        const val VERIFY_CONSENT_TRANSFORMATION_ID = "verify_consent"

        fun applyDecision(decision: ConsentDecision, dispatch: Dispatch): Dispatch? {
            val processedPurposes = dispatch.payload()
                .getDataList(Dispatch.Keys.PURPOSES_WITH_CONSENT_ALL) ?: DataList.EMPTY_LIST

            val unprocessedPurposes = decision.purposes.filter { purpose ->
                processedPurposes.find {
                    it.value == purpose
                } == null
            }
            if (unprocessedPurposes.isEmpty()) return null

            dispatch.addAll(DataObject.create {
                put(
                    Dispatch.Keys.PURPOSES_WITH_CONSENT_UNPROCESSED,
                    DataList.fromCollection(unprocessedPurposes)
                )
                put(Dispatch.Keys.PURPOSES_WITH_CONSENT_PROCESSED, processedPurposes)
                put(
                    Dispatch.Keys.PURPOSES_WITH_CONSENT_ALL,
                    DataList.fromCollection(decision.purposes)
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

    data class Factory(
        private val cmp: ConsentManagementAdapter,
        private val queueManager: QueueManager? = null,
        private val modules: ObservableState<List<Module>>? = null,
        private var settings: DataObject? = null
    ) : ModuleFactory {

        constructor(cmp: ConsentManagementAdapter, settings: ModuleSettingsBuilder) : this(
            cmp,
            null,
            null,
            settings.build()
        )

        override val id: String
            get() = NAME

        override fun create(context: TealiumContext, settings: DataObject): Module? {
            if (queueManager == null || modules == null) return null

            val consentSettings = ConsentSettings.fromDataObject(settings)

            return ConsentModule(
                modules,
                queueManager,
                context.transformerRegistry,
                cmp,
                Observables.stateSubject(consentSettings)
            )
        }

        override fun getEnforcedSettings(): DataObject? = settings
    }
}