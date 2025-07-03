package com.tealium.core.internal.consent

import com.tealium.core.api.consent.ConsentDecision
import com.tealium.core.api.data.DataItemUtils.asDataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.Module
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.internal.dispatch.QueueManager
import com.tealium.core.internal.settings.consent.ConsentConfiguration
import com.tealium.core.internal.settings.consent.ConsentSettings
import com.tealium.tests.common.SynchronousScheduler
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConsentIntegrationManagerTests {

    private lateinit var dispatch: Dispatch
    private lateinit var consentSettings: ObservableState<ConsentSettings?>
    private lateinit var modules: ObservableState<List<Module>>
    private lateinit var queueManager: QueueManager
    private lateinit var cmpAdapter: MockCmpAdapter
    private lateinit var cmpSelector: CmpConfigurationSelector
    private val consentManager: ConsentManager by lazy {
        ConsentIntegrationManager(modules, queueManager, cmpSelector)
    }

    @Before
    fun setUp(){
        dispatch = Dispatch.create("test")
        consentSettings = Observables.stateSubject(ConsentSettings(mapOf(
            "MockCmp" to ConsentConfiguration("tealium", emptySet(), emptyMap()))
        ))
        modules = Observables.stateSubject(listOf())
        cmpAdapter = MockCmpAdapter(id = "MockCmp")
        cmpSelector = CmpConfigurationSelector(consentSettings, cmpAdapter, SynchronousScheduler())
        queueManager = mockk(relaxed = true)
    }

    @Test
    fun applyConsent_Returns_Accepted_When_Implicitly_Not_Tealium_Consented() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Implicit, emptySet())

        val result = consentManager.applyConsent(dispatch)

        assertEquals(TrackResult.Accepted(dispatch), result)
    }

    @Test
    fun applyConsent_Returns_Dropped_When_Explicitly_Not_Tealium_Consented() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, emptySet())

        val result = consentManager.applyConsent(dispatch)

        assertEquals(TrackResult.Dropped(dispatch), result)
    }

    @Test
    fun applyConsent_Returns_Accepted_When_Tealium_Consented() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("tealium"))

        val result = consentManager.applyConsent(dispatch)

        assertEquals(TrackResult.Accepted(dispatch), result)
    }

    @Test
    fun applyConsent_Returns_Dropped_When_Unprocessed_Purposes_Empty() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("tealium"))

        dispatch.addAll(DataObject.create {
            put(Dispatch.Keys.PURPOSES_WITH_CONSENT_ALL, listOf("tealium").asDataList())
        })

        val result = consentManager.applyConsent(dispatch)

        assertEquals(TrackResult.Dropped(dispatch), result)
    }
}