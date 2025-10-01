package com.tealium.prism.core.internal.consent

import com.tealium.prism.core.api.consent.ConsentDecision
import com.tealium.prism.core.internal.settings.consent.ConsentConfiguration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsentInspectorTests {

    private val tealiumPurpose = "tealium_purpose"
    private lateinit var decision: ConsentDecision
    private var allPurposes: Set<String>? = null
    private lateinit var configuration: ConsentConfiguration
    private val consentInspector: ConsentInspector
        get() = ConsentInspector(configuration, decision, allPurposes)

    @Before
    fun setUp() {
        allPurposes = emptySet()
        configuration = ConsentConfiguration(
            tealiumPurpose,
            emptySet(),
            emptyMap()
        )
        decision = ConsentDecision(ConsentDecision.DecisionType.Implicit, emptySet())
    }

    @Test
    fun tealiumConsented_Returns_True_When_Purpose_Is_Accepted() {
        decision = ConsentDecision(ConsentDecision.DecisionType.Implicit, setOf(tealiumPurpose))

        assertTrue(consentInspector.tealiumConsented())
    }

    @Test
    fun tealiumConsented_Returns_False_When_Purpose_Is_Not_Accepted() {
        decision = ConsentDecision(ConsentDecision.DecisionType.Implicit, setOf("purpose1"))

        assertFalse(consentInspector.tealiumConsented())
    }

    @Test
    fun tealiumExplicitlyBlocked_Returns_True_When_Decision_Type_Is_Explicit_And_Tealium_Purpose_Is_Not_Accepted() {
        decision = ConsentDecision(ConsentDecision.DecisionType.Explicit, setOf("purpose1"))

        assertTrue(consentInspector.tealiumExplicitlyBlocked())
    }

    @Test
    fun tealiumExplicitlyBlocked_Returns_False_When_Decision_Type_Is_Explicit_And_Tealium_Purpose_Is_Accepted() {
        decision = ConsentDecision(ConsentDecision.DecisionType.Explicit, setOf(tealiumPurpose))

        assertFalse(consentInspector.tealiumExplicitlyBlocked())
    }

    @Test
    fun tealiumExplicitlyBlocked_Returns_False_When_Decision_Type_Is_Implicit_And_Tealium_Purpose_Is_Not_Accepted() {
        decision = ConsentDecision(ConsentDecision.DecisionType.Implicit, setOf("purpose1"))

        assertFalse(consentInspector.tealiumExplicitlyBlocked())
    }

    @Test
    fun tealiumExplicitlyBlocked_Returns_False_When_Decision_Type_Is_Implicit_And_Tealium_Purpose_Is_Accepted() {
        decision = ConsentDecision(ConsentDecision.DecisionType.Implicit, setOf(tealiumPurpose))

        assertFalse(consentInspector.tealiumExplicitlyBlocked())
    }

    @Test
    fun allowsRefire_Returns_True_When_Decision_Type_Is_Implicit_And_RefireIds_Is_Not_Empty_And_Not_All_Purposes_Consented() {
        decision = ConsentDecision(ConsentDecision.DecisionType.Implicit, setOf("1"))
        configuration = configuration.copy(refireDispatcherIds = setOf("dispatcher1"))
        allPurposes = setOf("1", "2", "3")

        assertTrue(consentInspector.allowsRefire())
    }

    @Test
    fun allowsRefire_Returns_False_When_Decision_Type_Is_Explicit() {
        decision = ConsentDecision(ConsentDecision.DecisionType.Explicit, setOf("1"))
        configuration = configuration.copy(refireDispatcherIds = setOf("dispatcher1"))
        allPurposes = setOf("1", "2", "3")

        assertFalse(consentInspector.allowsRefire())
    }

    @Test
    fun allowsRefire_Returns_False_When_Decision_Type_Is_Implicit_And_All_Purposes_Consented() {
        decision = ConsentDecision(ConsentDecision.DecisionType.Implicit, setOf("1", "2", "3"))
        configuration = configuration.copy(refireDispatcherIds = setOf("dispatcher1"))
        allPurposes = setOf("1", "2", "3")

        assertFalse(consentInspector.allowsRefire())
    }

    @Test
    fun allowsRefire_Returns_False_When_Refire_Ids_Is_Empty() {
        decision = ConsentDecision(ConsentDecision.DecisionType.Implicit, setOf("1"))
        configuration = configuration.copy(refireDispatcherIds = emptySet())
        allPurposes = setOf("1", "2", "3")

        assertFalse(consentInspector.allowsRefire())
    }

    @Test
    fun allowsRefire_Returns_True_When_AllPurposes_Is_Null() {
        decision = ConsentDecision(ConsentDecision.DecisionType.Implicit, setOf("1"))
        configuration = configuration.copy(refireDispatcherIds = setOf("dispatcher1"))
        allPurposes = null

        assertTrue(consentInspector.allowsRefire())
    }
}
