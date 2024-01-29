package com.tealium.core.internal.dispatch

import com.tealium.core.api.ConsentDecision
import com.tealium.core.api.DispatchScope
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DispatchManagerConsentTests: DispatchManagerTestsBase() {

    @Test
    fun track_StoresDispatch_WhenConsentIsDisabled() = runTest {
        every { consentManager.enabled } returns false

        dispatchManager.track(dispatch1)

        coVerify {
            transformerCoordinator.transform(dispatch1, DispatchScope.AfterCollectors)
            queueManager.storeDispatch(dispatch1, any())
        }
        verify(inverse = true) {
            consentManager.getConsentDecision()
        }
    }

    @Test
    fun track_AppliesConsent_WhenDecision_IsImplicit() = runTest {
        every { consentManager.enabled } returns true
        every { consentManager.getConsentDecision() } returns ConsentDecision(
            ConsentDecision.DecisionType.Implicit,
            emptySet()
        )

        dispatchManager.track(dispatch1)

        coVerify {
            consentManager.applyConsent(dispatch1)
            queueManager wasNot Called
        }
    }

    @Test
    fun track_DoesNothing_WhenTealiumPurposeIsNotConsented() = runTest {
        every { consentManager.enabled } returns true
        every { consentManager.getConsentDecision() } returns ConsentDecision(
            ConsentDecision.DecisionType.Explicit,
            emptySet()
        )
        every { consentManager.tealiumConsented(any()) } returns false

        dispatchManager.track(dispatch1)

        coVerify {
            queueManager wasNot Called
            transformerCoordinator wasNot Called
        }
    }

    @Test
    fun track_RunsTransformations_WhenTealiumPurposeIsNotBlocked() = runTest {
        every { consentManager.enabled } returns true
        every { consentManager.getConsentDecision() } returns ConsentDecision(
            ConsentDecision.DecisionType.Explicit,
            emptySet()
        )
        every { consentManager.tealiumConsented(any()) } returns true

        dispatchManager.track(dispatch1)

        coVerify {
            transformerCoordinator.transform(dispatch1, DispatchScope.AfterCollectors)
            consentManager.applyConsent(dispatch1)
            queueManager wasNot Called
        }
    }

    @Test
    fun track_DropsDispatch_WhenTransformerReturnsNull() = runTest {
        every { consentManager.enabled } returns false
        coEvery { transformerCoordinator.transform(dispatch1, DispatchScope.AfterCollectors) } returns null

        dispatchManager.track(dispatch1)

        coVerify {
            transformerCoordinator.transform(dispatch1, DispatchScope.AfterCollectors)
            queueManager wasNot Called
        }
        coVerify(inverse = true) {
            consentManager.applyConsent(dispatch1)
        }
    }
}