package com.tealium.prism.core.internal.consent

import com.tealium.prism.core.api.consent.ConsentDecision
import com.tealium.prism.core.api.consent.ConsentDecision.DecisionType
import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.settings.consent.ConsentConfiguration
import com.tealium.prism.core.internal.settings.consent.ConsentPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentUtilsTests {

    private val dispatch = Dispatch.create("test")

    @Test
    fun applyDecision_Returns_Null_When_No_Purposes_Provided() {
        assertNull(dispatch.applyDecision(ConsentDecision(DecisionType.Explicit, setOf())))
    }

    @Test
    fun applyDecision_Returns_Null_If_Previously_Processed_Purposes_Contain_All_Consented_Purposes() {
        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("1", "2", "3").asDataList())
        })
        assertNull(dispatch.applyDecision(ConsentDecision(DecisionType.Explicit, setOf("1", "2"))))
    }

    @Test
    fun applyDecision_Returns_Dispatch_If_Consented_Purposes_Contain_Some_New_Unprocessed_Purposes() {
        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("1", "2").asDataList())
        })

        val consentedDispatch = dispatch.applyDecision(
            ConsentDecision(DecisionType.Explicit, setOf("1", "3"))
        )
        assertNotNull(consentedDispatch)
    }

    @Test
    fun applyDecision_Returns_Dispatch_With_AllPurposes_Being_The_Newly_Consented_List_Of_Purposes() {
        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("1", "2").asDataList())
        })

        val consentedDispatch = dispatch.applyDecision(
            ConsentDecision(DecisionType.Explicit, setOf("1", "3"))
        )!!

        val allPurposes = consentedDispatch.allConsentedPurposes
        assertEquals(allPurposes, setOf("1", "3"))
    }

    @Test
    fun applyDecision_Returns_Dispatch_With_ProcessedPurposes_Being_The_Old_Consented_List_Of_AllPurposes() {
        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("1", "2").asDataList())
        })

        val consentedDispatch = dispatch.applyDecision(
            ConsentDecision(DecisionType.Explicit, setOf("1", "3"))
        )!!

        val processedPurposes = consentedDispatch.processedPurposes
        assertEquals(processedPurposes, setOf("1", "2"))
    }

    @Test
    fun applyDecision_Returns_Dispatch_With_UnprocessedPurposes_Being_The_Newly_Consented_List_Of_Purposes_Minus_The_Old_Consented_Purposes() {
        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("1", "2").asDataList())
        })

        val consentedDispatch = dispatch.applyDecision(
            ConsentDecision(DecisionType.Explicit, setOf("1", "3"))
        )!!

        val unprocessedPurposes = consentedDispatch.unprocessedPurposes
        assertEquals(unprocessedPurposes, setOf("3"))
    }

    @Test
    fun applyDecision_Returns_Dispatch_With_DecisionType_Explicit() {
        val consentedDispatch = dispatch.applyDecision(
            ConsentDecision(DecisionType.Explicit, setOf("1", "3"))
        )!!

        val consentType = consentedDispatch.payload()
            .getString(Dispatch.Keys.CONSENT_TYPE)

        assertEquals(consentType, "explicit")
    }

    @Test
    fun applyDecision_Returns_Dispatch_With_DecisionType_Implicit() {
        val consentedDispatch = dispatch.applyDecision(
            ConsentDecision(
                DecisionType.Implicit,
                setOf("1", "3")
            )
        )!!
        val consentType = consentedDispatch.payload()
            .getString(Dispatch.Keys.CONSENT_TYPE)

        assertEquals(consentType, "implicit")
    }

    @Test
    fun matchesConfiguration_Returns_True_When_AllPurposes_Required_By_Dispatcher_Are_Granted() {
        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("1", "2").asDataList())
        })
        val purposeSettings = mapOf(
            "1" to ConsentPurpose("1", setOf("dispatcher")),
            "2" to ConsentPurpose("2", setOf("dispatcher")),
            "3" to ConsentPurpose("3", setOf("other_dispatcher"))
        )
        val configuration =
            ConsentConfiguration("", emptySet(), purposeSettings)

        assertTrue(
            dispatch.matchesConfiguration(configuration, "dispatcher")
        )
    }

    @Test
    fun matchesConfiguration_Returns_False_When_At_Least_One_Purpose_Required_By_Dispatcher_Is_Not_Granted() {
        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("1", "2").asDataList())
        })
        val purposeSettings = mapOf(
            "1" to ConsentPurpose("1", setOf("dispatcher")),
            "2" to ConsentPurpose("2", setOf("dispatcher")),
            "3" to ConsentPurpose("3", setOf("dispatcher"))
        )
        val configuration =
            ConsentConfiguration("", emptySet(), purposeSettings)

        assertFalse(
            dispatch.matchesConfiguration(configuration, "dispatcher")
        )
    }

    @Test
    fun matchesConfiguration_Returns_False_When_AllPurposes_Are_Not_Present_In_Payload() {
        val purposeSettings = mapOf(
            "1" to ConsentPurpose("1", setOf("dispatcher")),
            "2" to ConsentPurpose("2", setOf("dispatcher")),
            "3" to ConsentPurpose("3", setOf("dispatcher"))
        )
        val configuration =
            ConsentConfiguration("", emptySet(), purposeSettings)

        assertFalse(
            dispatch.matchesConfiguration(configuration, "dispatcher")
        )
    }

    @Test
    fun matchesConfiguration_Returns_False_When_AllPurposes_Are_Empty_Even_If_Dispatcher_Needs_No_Purposes() {
        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, DataList.EMPTY_LIST)
        })
        val configuration =
            ConsentConfiguration("", emptySet(), mapOf())

        assertFalse(
            dispatch.matchesConfiguration(configuration, "dispatcher")
        )
    }

    @Test
    fun matchesConfiguration_Returns_False_When_Dispatcher_Has_No_Required_Purpose() {
        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf("1").asDataList())
        })
        val purposeSettings =
            mapOf(
                "1" to ConsentPurpose("1", setOf("dispatcher")),
                "2" to ConsentPurpose("2", setOf("dispatcher")),
                "3" to ConsentPurpose("3", setOf("dispatcher"))
            )
        val configuration =
            ConsentConfiguration("", emptySet(), purposeSettings)

        assertFalse(
            dispatch.matchesConfiguration(configuration, "other_dispatcher")
        )
    }

    private val Dispatch.allConsentedPurposes: Set<String>?
        get() = getStringList(Dispatch.Keys.ALL_CONSENTED_PURPOSES)

    private val Dispatch.processedPurposes: Set<String>?
        get() = getStringList(Dispatch.Keys.PROCESSED_PURPOSES)

    private val Dispatch.unprocessedPurposes: Set<String>?
        get() = getStringList(Dispatch.Keys.UNPROCESSED_PURPOSES)

    private fun Dispatch.getStringList(key: String): Set<String>? = payload()
        .getDataList(key)
        ?.mapNotNull(DataItem::getString)
        ?.toSet()

}