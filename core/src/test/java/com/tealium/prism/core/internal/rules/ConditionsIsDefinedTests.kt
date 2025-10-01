package com.tealium.prism.core.internal.rules

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.rules.Condition
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConditionsIsDefinedTests {

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
    fun isDefined_Matches_All_Keys_In_Payload() {
        payload.forEach { (key, _) ->
            val condition = Condition.isDefined(
                variable = key,
            )
            assertTrue(condition.matches(payload))
        }
    }

    @Test
    fun isDefined_Does_Not_Match_Missing_Keys() {
        val condition = Condition.isDefined(
            variable = "missing",
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun isNotDefined_Does_Not_Match_String_With_Different_Casing() {
        payload.forEach { (key, _) ->
            val condition = Condition.isNotDefined(
                variable = key,
            )
            assertFalse(condition.matches(payload))
        }
    }

    @Test
    fun isNotDefined_Matches_Missing_Keys() {
        val condition = Condition.isNotDefined(
            variable = "missing",
        )
        assertTrue(condition.matches(payload))
    }
}