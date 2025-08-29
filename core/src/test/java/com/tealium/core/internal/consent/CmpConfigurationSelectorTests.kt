package com.tealium.core.internal.consent

import com.tealium.core.api.consent.ConsentDecision
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.internal.settings.consent.ConsentConfiguration
import com.tealium.core.internal.settings.consent.ConsentSettings
import com.tealium.tests.common.SynchronousScheduler
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CmpConfigurationSelectorTests {

    private val configuration = ConsentConfiguration("tealium_purpose", emptySet(), emptyMap())
    private lateinit var consentSettings: StateSubject<ConsentSettings?>
    private lateinit var cmpAdapter: MockCmpAdapter
    private lateinit var selector: CmpConfigurationSelector

    @Before
    fun setUp() {
        cmpAdapter = MockCmpAdapter()
        consentSettings = Observables.stateSubject(null)

        selector = CmpConfigurationSelector(consentSettings, cmpAdapter, SynchronousScheduler())
    }

    @Test
    fun configuration_Is_Selected_When_CmpAdapterId_Matches_VendorId() {
        consentSettings.onNext(ConsentSettings(mapOf("vendor1" to configuration)))

        val selectedConfiguration = selector.configuration.value!!
        assertEquals(configuration, selectedConfiguration)
    }

    @Test
    fun configuration_Is_Not_Selected_When_CmpAdapterId_Does_Not_Match_VendorId() {
        consentSettings.onNext(ConsentSettings(mapOf("wrong_vendor" to configuration)))

        val selectedConfiguration = selector.configuration.value
        assertNull(selectedConfiguration)
    }

    @Test
    fun consentInspector_Is_Null_Until_Configuration_And_Decision_Are_Provided() {
        assertNull(selector.consentInspector.value)

        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf())
        assertNull(selector.consentInspector.value)


        consentSettings.onNext(ConsentSettings(mapOf("vendor1" to configuration)))
        assertNotNull(selector.consentInspector.value)
    }

    @Test
    fun consentInspector_Does_Not_Emit_Again_When_ConsentDecision_Is_The_Same() {
        val observer = mockk<Observer<ConsentInspector?>>(relaxed = true)
        consentSettings.onNext(ConsentSettings(mapOf("vendor1" to configuration)))

        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("purpose_1"))
        selector.consentInspector.subscribe(observer)

        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("purpose_1"))
        cmpAdapter.setDecision(ConsentDecision.DecisionType.Explicit, setOf("purpose_1"))

        verify(exactly = 1) {
            observer.onNext(match {
                it.configuration == configuration
                        && it.decision == ConsentDecision(ConsentDecision.DecisionType.Explicit, setOf("purpose_1"))
            })
        }
    }
}