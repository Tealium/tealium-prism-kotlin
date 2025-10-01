package com.tealium.prism.core.internal.consent

import com.tealium.prism.core.api.consent.ConsentDecision
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.internal.dispatch.QueueManager
import com.tealium.prism.core.internal.settings.consent.ConsentConfiguration
import com.tealium.prism.core.internal.settings.consent.ConsentPurpose
import com.tealium.prism.core.internal.settings.consent.ConsentSettings
import com.tealium.tests.common.SynchronousScheduler
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestDispatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConsentIntegrationManagerTests {

    private lateinit var dispatch: Dispatch
    private lateinit var consentSettings: StateSubject<ConsentSettings?>
    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var queueManager: QueueManager
    private lateinit var cmpAdapter: MockCmpAdapter
    private lateinit var cmpSelector: CmpConfigurationSelector
    private lateinit var consentManager: ConsentManager

    @Before
    fun setUp() {
        dispatch = Dispatch.create("test")
        consentSettings = Observables.stateSubject(
            ConsentSettings(
                mapOf(
                    "MockCmp" to ConsentConfiguration(
                        "tealium", setOf("dispatcher_1"), mapOf(
                            "1" to ConsentPurpose("1", setOf("dispatcher_1")),
                            "2" to ConsentPurpose("2", setOf("dispatcher_2"))
                        )
                    )
                )
            )
        )
        modules = Observables.stateSubject(
            listOf(
                TestDispatcher("dispatcher_1"),
                TestDispatcher("dispatcher_2")
            )
        )
        cmpAdapter = MockCmpAdapter(id = "MockCmp", setOf("tealium", "1", "2", "3"))
        cmpSelector = CmpConfigurationSelector(consentSettings, cmpAdapter, SynchronousScheduler())
        queueManager = mockk(relaxed = true)

        consentManager = ConsentIntegrationManager(modules, queueManager, cmpSelector, SystemLogger)
    }

    @Test
    fun applyConsent_Returns_Accepted_When_Implicitly_Not_Tealium_Consented() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Implicit, emptySet())

        val result = consentManager.applyConsent(dispatch)

        assertEquals(TrackResult.Status.Accepted, result.status)
    }

    @Test
    fun applyConsent_Returns_Dropped_When_Explicitly_Not_Tealium_Consented() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, emptySet())

        val result = consentManager.applyConsent(dispatch)

        assertEquals(TrackResult.Status.Dropped, result.status)
    }

    @Test
    fun applyConsent_Returns_Accepted_When_Tealium_Consented() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("tealium"))

        val result = consentManager.applyConsent(dispatch)

        assertEquals(TrackResult.Status.Accepted, result.status)
    }

    @Test
    fun applyConsent_Returns_Dropped_When_Unprocessed_Purposes_Empty() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("tealium"))

        dispatch.addAll(DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("tealium").asDataList())
        })

        val result = consentManager.applyConsent(dispatch)

        assertEquals(TrackResult.Status.Dropped, result.status)
    }

    @Test
    fun applyConsent_Enqueues_For_Consent_When_Missing_Configuration() {
        consentSettings.onNext(ConsentSettings(emptyMap()))

        consentManager.applyConsent(dispatch)

        verify {
            queueManager.storeDispatches(listOf(dispatch), setOf(ConsentIntegrationManager.ID))
        }
    }

    @Test
    fun applyConsent_Enqueues_For_Consent_When_Tealium_Implicitly_Not_Consented() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Implicit, emptySet())

        consentManager.applyConsent(dispatch)

        verify {
            queueManager.storeDispatches(listOf(dispatch), setOf(ConsentIntegrationManager.ID))
        }
    }

    @Test
    fun applyConsent_Enqueues_For_Refire_When_Unprocessed_Purposes_Are_Available() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Implicit, setOf("tealium", "1", "2"))
        dispatch.addAll(DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("1").asDataList())
        })

        consentManager.applyConsent(dispatch)

        verify {
            queueManager.storeDispatches(
                listOf(dispatch),
                setOf("dispatcher_1", "dispatcher_2", ConsentIntegrationManager.ID)
            )
        }
    }

    @Test
    fun applyConsent_Does_Not_Enqueue_When_Tealium_Explicitly_Blocked() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, emptySet())

        consentManager.applyConsent(dispatch)

        verify(inverse = true) {
            queueManager.storeDispatches(listOf(dispatch), any())
        }
    }

    @Test
    fun applyConsent_Does_Not_Enqueue_When_No_Additional_Purposes_To_Process() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("1", "2"))
        dispatch.addAll(DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("1", "2").asDataList())
        })

        consentManager.applyConsent(dispatch)

        verify(inverse = true) {
            queueManager.storeDispatches(listOf(dispatch), any())
        }
    }

    @Test
    fun consentDecision_Deletes_Consent_Queue_When_ConsentDecision_Changes() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("tealium", "1"))

        verify { queueManager.deleteAllDispatches(ConsentIntegrationManager.ID) }
    }

    @Test
    fun consentDecision_Does_Not_ReQueue_When_Tealium_Explicitly_Blocked() {
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, emptySet())

        verify(inverse = true) {
            queueManager.storeDispatches(any(), any())
        }
    }

    @Test
    fun consentDecision_Does_Not_ReQueue_When_No_Dispatches_Queued() {
        every {
            queueManager.dequeueDispatches(
                -1,
                ConsentIntegrationManager.ID
            )
        } returns emptyList()
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("tealium", "1", "2"))

        verify(inverse = true) {
            queueManager.storeDispatches(any(), any())
        }
    }

    @Test
    fun consentDecision_Enqueues_For_Refire_When_Dispatch_Has_New_Purposes() {
        val event1 = Dispatch.create("event_1")
        val refireable = Dispatch.create("refireable", dataObject = DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("tealium", "1").asDataList())
        })
        every { queueManager.dequeueDispatches(-1, ConsentIntegrationManager.ID) } returns listOf(
            event1, refireable
        )
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Implicit, setOf("tealium", "1", "2"))

        verify {
            queueManager.storeDispatches(
                match { it.size == 1 && it[0].tealiumEvent == "event_1" },
                setOf("dispatcher_1", "dispatcher_2")
            )
            queueManager.storeDispatches(
                match {
                    it.size == 1
                            && it[0].id == "${refireable.id}-refire"
                            && it[0].tealiumEvent == "refireable"
                },
                setOf("dispatcher_1")
            )
        }
    }
}