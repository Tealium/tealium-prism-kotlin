package com.tealium.core.internal.consent

import com.tealium.core.api.consent.ConsentDecision
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.internal.settings.consent.ConsentConfiguration
import com.tealium.core.internal.settings.consent.ConsentSettings
import com.tealium.tests.common.SynchronousScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CmpConfigurationSelectorTests {

    private val configuration = ConsentConfiguration("tealium_purpose", emptySet(), emptyMap())
    private lateinit var consentSettings: StateSubject<ConsentSettings?>
    private lateinit var cmpAdapter: MockCmpAdapter
    private val selector: CmpConfigurationSelector by lazy {
        CmpConfigurationSelector(consentSettings, cmpAdapter, SynchronousScheduler())
    }

    @Before
    fun setUp() {
        cmpAdapter = MockCmpAdapter()
        consentSettings = Observables.stateSubject(null)
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

}