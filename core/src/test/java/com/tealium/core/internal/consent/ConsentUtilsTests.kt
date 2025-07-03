package com.tealium.core.internal.consent

import com.tealium.core.api.consent.ConsentDecision
import com.tealium.core.api.tracking.Dispatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentUtilsTests {

    private val allPurposes = setOf(
        "purpose_1",
        "purpose_2",
        "purpose_3",
        "purpose_4",
        "purpose_5",
        "tealium"
    )

    // TODO - the rest of the tests.

    @Test
    fun applyDecision_On_New_Dispatch_Adds_All_Purposes() {
        val dispatch = Dispatch.create("test")

        dispatch.applyDecision(
            ConsentDecision(
                ConsentDecision.DecisionType.Implicit,
                allPurposes
            )
        )

        dispatch.payload().getDataList(Dispatch.Keys.PURPOSES_WITH_CONSENT_UNPROCESSED)!!.forEach {
            assertTrue(
                allPurposes.contains(it.getString())
            )
        }
        assertEquals(0, dispatch.payload().getDataList(Dispatch.Keys.PURPOSES_WITH_CONSENT_PROCESSED)!!.size)
    }
}