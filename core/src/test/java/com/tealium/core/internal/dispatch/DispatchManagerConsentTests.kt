package com.tealium.core.internal.dispatch

import com.tealium.core.api.consent.ConsentDecision
import com.tealium.core.api.data.DataItemUtils.asDataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.Module
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.api.transform.DispatchScope
import com.tealium.core.internal.consent.CmpConfigurationSelector
import com.tealium.core.internal.consent.ConsentInspector
import com.tealium.core.internal.consent.ConsentIntegrationManager
import com.tealium.core.internal.consent.ConsentManager
import com.tealium.core.internal.consent.MockCmpAdapter
import com.tealium.core.internal.settings.consent.ConsentConfiguration
import com.tealium.core.internal.settings.consent.ConsentPurpose
import com.tealium.tests.common.SystemLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchManagerConsentTests : DispatchManagerTestsBase() {

    private fun disableConsent() {
        dispatchManager = createDispatchManager(consentManager = null)
    }

    private fun enableConsent(consentManager: ConsentManager) {
        dispatchManager = createDispatchManager(consentManager = consentManager)
    }

    @Test
    fun track_Stores_Dispatch_When_Consent_Is_Disabled() {
        disableConsent()

        dispatchManager.track(dispatch1)

        verify {
            queueManager.storeDispatches(listOf(dispatch1), any())
        }
    }

    @Test
    fun track_Applies_Consent_To_Dispatch_When_Consent_Is_Enabled() {
        val consentManager = mockConsentManager(accepted = true)
        enableConsent(consentManager)

        dispatchManager.track(dispatch1)

        verify {
            consentManager.applyConsent(dispatch1)
        }
    }

    @Test
    fun track_Notifies_Dispatch_Accepted_When_Consent_Manager_Returns_Accepted() {
        enableConsent(mockConsentManager(accepted = true))
        val onComplete: (TrackResult) -> Unit = mockk(relaxed = true)

        dispatchManager.track(dispatch1, onComplete)

        verify {
            onComplete(match {
                it.status == TrackResult.Status.Accepted
            })
        }
    }

    @Test
    fun track_Notifies_Dispatch_Dropped_When_Consent_Manager_Returns_Dropped() {
        enableConsent(mockConsentManager(accepted = false))
        val onComplete: (TrackResult) -> Unit = mockk(relaxed = true)

        dispatchManager.track(dispatch1, onComplete)

        verify {
            onComplete(match {
                it.status == TrackResult.Status.Dropped
            })
        }
    }

    @Test
    fun track_Notifies_Dispatch_Dropped_When_Tealium_Consent_Explicitly_Blocked() {
        enableConsent(mockConsentManager(tealiumExplicitlyBlocked = true))
        val onComplete: (TrackResult) -> Unit = mockk(relaxed = true)

        dispatchManager.track(dispatch1, onComplete)

        verify {
            onComplete(match {
                it.status == TrackResult.Status.Dropped
            })
        }
    }

    @Test
    fun track_Runs_Transformations_When_Tealium_Purpose_Is_Not_Blocked() {
        enableConsent(mockConsentManager(accepted = true))

        dispatchManager.track(dispatch1)

        verify {
            transformerCoordinator.transform(dispatch1, DispatchScope.AfterCollectors, any())
        }
    }

    @Test
    fun startDispatchLoop_Dequeues_Events_When_Consent_Disabled() {
        disableConsent()
        queueManager.storeDispatches(
            listOf(dispatch1, dispatch2),
            modules.value.map(Module::id).toSet()
        )

        dispatchManager.startDispatchLoop()

        verify {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch2), any())
        }
    }

    @Test
    fun startDispatchLoop_Does_Not_Dequeue_Events_When_Consent_Enabled_And_No_Decision() {
        val cmpSelector = mockk<CmpConfigurationSelector>()
        every { cmpSelector.cmpAdapter } returns MockCmpAdapter()
        every { cmpSelector.consentInspector } returns Observables.stateSubject(null)
        every { cmpSelector.configuration } returns Observables.stateSubject(null)
        enableConsent(ConsentIntegrationManager(modules, queueManager, cmpSelector, SystemLogger))
        queueManager.storeDispatches(
            listOf(dispatch1, dispatch2),
            modules.value.map(Module::id).toSet()
        )

        dispatchManager.startDispatchLoop()

        verify(inverse = true) {
            queueManager.dequeueDispatches(any(), any())
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch2), any())
        }
    }

    @Test
    fun startDispatchLoop_Processes_Events_That_Are_Accepted_By_Consent_And_Deletes_Them() {
        val consentInspector = ConsentInspector(
            ConsentConfiguration(
                "tealium",
                emptySet(),
                mapOf("purpose1" to ConsentPurpose("purpose1", setOf(dispatcher1Name)))
            ),
            ConsentDecision(ConsentDecision.DecisionType.Explicit, setOf("purpose1")),
            setOf("purpose1")
        )
        val cmpSelector = mockk<CmpConfigurationSelector>()
        every { cmpSelector.consentInspector } returns Observables.stateSubject(consentInspector)
        every { cmpSelector.configuration } returns Observables.stateSubject(consentInspector.configuration)
        enableConsent(ConsentIntegrationManager(modules, queueManager, cmpSelector, SystemLogger))
        dispatch1.addAll(DataObject.create { put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("purpose1").asDataList()) })
        dispatch2.addAll(DataObject.create { put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("purpose1").asDataList()) })
        queueManager.storeDispatches(
            listOf(dispatch1, dispatch2),
            modules.value.map(Module::id).toSet()
        )

        dispatchManager.startDispatchLoop()

        verify {
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
        }
        verify {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch2), any())
        }
    }

    @Test
    fun startDispatchLoop_Deletes_Events_That_Are_Dropped_By_Consent() {
        val consentInspector = ConsentInspector(
            ConsentConfiguration("tealium", emptySet(), emptyMap()),
            ConsentDecision(ConsentDecision.DecisionType.Explicit, emptySet()),
            emptySet()
        )
        val cmpSelector = mockk<CmpConfigurationSelector>()
        every { cmpSelector.consentInspector } returns Observables.stateSubject(consentInspector)
        every { cmpSelector.configuration } returns Observables.stateSubject(consentInspector.configuration)
        enableConsent(ConsentIntegrationManager(modules, queueManager, cmpSelector, SystemLogger))
        queueManager.storeDispatches(
            listOf(dispatch1, dispatch2),
            modules.value.map(Module::id).toSet()
        )

        dispatchManager.startDispatchLoop()

        verify {
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
        }
        verify(inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch2), any())
        }
    }

    @Test
    fun tealiumPurposeExplicitlyBlocked_Returns_False_When_ConsentManager_Null() {
        assertFalse(dispatchManager.tealiumPurposeExplicitlyBlocked)
    }

    @Test
    fun tealiumPurposeExplicitlyBlocked_Returns_False_When_Tealium_Purpose_Consented() {
        consentManager = mockConsentManager(tealiumExplicitlyBlocked = false)
        dispatchManager = createDispatchManager(consentManager = consentManager)
        assertFalse(dispatchManager.tealiumPurposeExplicitlyBlocked)
    }

    @Test
    fun tealiumPurposeExplicitlyBlocked_Returns_True_When_Tealium_Purpose_Explicitly_Not_Consented() {
        consentManager = mockConsentManager(tealiumExplicitlyBlocked = true)
        dispatchManager = createDispatchManager(consentManager = consentManager)
        assertTrue(dispatchManager.tealiumPurposeExplicitlyBlocked)
    }

    private fun mockConsentManager(
        tealiumExplicitlyBlocked: Boolean = false,
        configuration: Observable<ConsentConfiguration?> = Observables.just(null),
        accepted: Boolean = true
    ): ConsentManager {
        return spyk(object : ConsentManager {
            override val tealiumPurposeExplicitlyBlocked: Boolean
                get() = tealiumExplicitlyBlocked
            override val configuration: Observable<ConsentConfiguration?>
                get() = configuration

            override fun applyConsent(dispatch: Dispatch): TrackResult =
                if (accepted) TrackResult.accepted(dispatch, "") else TrackResult.dropped(dispatch, "")
        })
    }
}