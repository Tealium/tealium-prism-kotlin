package com.tealium.core.internal.rules

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Condition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConditionsIsBadgeAssignedTests {

    private val list = DataList.create { add("a"); add("b"); add("c") }
    private val payload = DataObject.create {
        put("string", "Value")
        put("int", 345)
        put("double", 3.14)
        put("bool", true)
        put("list", list)
        put("object", DataObject.Builder().put("key", "Value").build())
        put("null", DataItem.NULL)
    }

    @Test
    fun isBadgeAssigned_Matches_All_Keys_In_Payload() {
        payload.forEach { (key, _) ->
            val condition = Condition.isBadgeAssigned(
                variable = key,
            )
            assertTrue(condition.matches(payload))
        }
    }

    @Test
    fun isBadgeAssigned_Does_Not_Match_Missing_Keys() {
        val condition = Condition.isBadgeAssigned(
            variable = "missing",
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun isBadgeNotAssigned_Does_Not_Match_String_With_Different_Casing() {
        payload.forEach { (key, _) ->
            val condition = Condition.isBadgeNotAssigned(
                variable = key,
            )
            assertFalse(condition.matches(payload))
        }
    }

    @Test
    fun isBadgeNotAssigned_Matches_Missing_Keys() {
        val condition = Condition.isBadgeNotAssigned(
            variable = "missing",
        )
        assertTrue(condition.matches(payload))
    }
}