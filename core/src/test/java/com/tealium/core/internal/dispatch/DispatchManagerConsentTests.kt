package com.tealium.core.internal.dispatch

import com.tealium.core.api.modules.consent.ConsentDecision
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.transform.DispatchScope
import com.tealium.core.api.tracking.TrackResult
import io.mockk.Called
import io.mockk.Runs
import io.mockk.every
import io.mockk.slot
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class DispatchManagerConsentTests : DispatchManagerTestsBase() {

    @Test
    fun track_StoresDispatch_WhenConsentIsDisabled() {
        disableConsent()

        dispatchManager.track(dispatch1)

        verify {
            transformerCoordinator.transform(dispatch1, DispatchScope.AfterCollectors, any())
            queueManager.storeDispatches(listOf(dispatch1), any())
        }
        verify(inverse = true) {
            consentManager.getConsentDecision()
        }
    }

    @Test
    fun track_AppliesConsent_WhenDecision_IsImplicit() {
        enableConsent()
        every { consentManager.getConsentDecision() } returns ConsentDecision(
            ConsentDecision.DecisionType.Implicit,
            emptySet()
        )

        dispatchManager.track(dispatch1)

        verify {
            consentManager.applyConsent(dispatch1)
            queueManager wasNot Called
        }
    }

    @Test
    fun track_DoesNothing_WhenTealiumPurposeIsNotConsented() {
        enableConsent()
        every { consentManager.getConsentDecision() } returns ConsentDecision(
            ConsentDecision.DecisionType.Explicit,
            emptySet()
        )
        every { consentManager.tealiumConsented(any()) } returns false

        dispatchManager.track(dispatch1)

        verify {
            queueManager wasNot Called
            transformerCoordinator wasNot Called
        }
    }

    @Test
    fun track_RunsTransformations_WhenTealiumPurposeIsNotBlocked() {
        enableConsent()
        every { consentManager.getConsentDecision() } returns ConsentDecision(
            ConsentDecision.DecisionType.Explicit,
            emptySet()
        )
        every { consentManager.tealiumConsented(any()) } returns true

        dispatchManager.track(dispatch1)

        verify {
            transformerCoordinator.transform(dispatch1, DispatchScope.AfterCollectors, any())
            consentManager.applyConsent(dispatch1)
            queueManager wasNot Called
        }
    }

    @Test
    fun track_DropsDispatch_WhenTransformerReturnsNull() {
        disableConsent()
        val completionCapture = slot<(Dispatch?) -> Unit>()
        every {
            transformerCoordinator.transform(
                dispatch1,
                DispatchScope.AfterCollectors,
                capture(completionCapture)
            )
        } answers {
            completionCapture.captured(null)
        }

        dispatchManager.track(dispatch1)

        verify {
            transformerCoordinator.transform(dispatch1, DispatchScope.AfterCollectors, any())
            queueManager wasNot Called
        }
        verify(inverse = true) {
            consentManager.applyConsent(dispatch1)
        }
    }

    @Test
    fun track_Notifies_DispatchAccepted_WhenDispatchQueued_ForConsent() {
        enableConsent()
        every { consentManager.getConsentDecision() } returns null
        every { consentManager.applyConsent(any()) } just Runs
        val onComplete: (Dispatch, TrackResult) -> Unit = mockk(relaxed = true)

        dispatchManager.track(dispatch1, onComplete)

        verify {
            onComplete(dispatch1, TrackResult.Accepted)
        }
    }

    @Test
    fun track_Notifies_DispatchDropped_WhenTealiumConsent_Denied() {
        enableConsent()
        every { consentManager.getConsentDecision() } returns ConsentDecision(ConsentDecision.DecisionType.Explicit, setOf())
        every { consentManager.tealiumConsented(any()) } returns false
        val onComplete: (Dispatch, TrackResult) -> Unit = mockk(relaxed = true)

        dispatchManager.track(dispatch1, onComplete)

        verify {
            onComplete(dispatch1, TrackResult.Dropped)
        }
    }
}