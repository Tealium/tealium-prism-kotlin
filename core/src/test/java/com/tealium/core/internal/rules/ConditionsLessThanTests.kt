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
class ConditionsLessThanTests {

    private val list = DataList.create { add("a"); add("b"); add("c") }
    private val payload = DataObject.create {
        put("string", "Value")
        put("int", 345)
        put("double", 3.14)
        put("bool", true)
        put("list", list)
        put("object", DataObject.Builder().put("key", 345).build())
        put("null", DataItem.NULL)
    }

    @Test
    fun lessThan_Does_Not_Match_String() {
        val condition = Condition.isLessThan(
            orEqual = false,
            variable = "string",
            number = "Value"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun lessThan_Does_Not_Match_Boolean() {
        val condition = Condition.isLessThan(
            orEqual = false,
            variable = "bool",
            number = "true"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun lessThan_Does_Not_Match_Array() {
        val condition = Condition.isLessThan(
            orEqual = false,
            variable = "list",
            number = list.toString()
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun lessThan_Does_Not_Match_Objects() {
        val condition = Condition.isLessThan(
            orEqual = false,
            variable = "object",
            number = payload.getDataObject("object").toString()
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun lessThan_Does_Not_Match_If_Int_Greater_Than_Filter() {
        val condition = Condition.isLessThan(
            orEqual = false,
            variable = "int",
            number = "344"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun lessThan_Does_Not_Match_If_Double_Greater_Than_Filter() {
        val condition = Condition.isLessThan(
            orEqual = false,
            variable = "double",
            number = "3.13"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun lessThan_Does_Not_Match_If_Long_Greater_Than_Filter() {
        val payload = payload.copy {
            put("long", Int.MAX_VALUE.toLong() + 100)
        }
        val condition = Condition.isLessThan(
            orEqual = false,
            variable = "long",
            number = Int.MAX_VALUE.toString()
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun lessThan_Does_Not_Match_If_Int_Equals_Filter() {
        val condition = Condition.isLessThan(
            orEqual = false,
            variable = "int",
            number = "345"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun lessThan_Does_Not_Match_If_Double_Equals_Filter() {
        val condition = Condition.isLessThan(
            orEqual = false,
            variable = "double",
            number = "3.14"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun lessThan_Does_Not_Match_If_Long_Equals_Filter() {
        val long = Int.MAX_VALUE.toLong() + 100
        val payload = payload.copy {
            put("long", long)
        }
        val condition = Condition.isLessThan(
            orEqual = false,
            variable = "long",
            number = long.toString()
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun lessThan_Does_Not_Match_If_Int_Less_Than_Filter() {
        val condition = Condition.isLessThan(
            orEqual = false,
            variable = "int",
            number = "346"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun lessThan_Matches_Double_Less_Than_Filter() {
        val condition = Condition.isLessThan(
            orEqual = false,
            variable = "double",
            number = "3.2"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun lessThan_Matches_If_Long_Less_Than_Filter() {
        val long = Int.MAX_VALUE.toLong() + 100
        val payload = payload.copy {
            put("long", long)
        }
        val condition = Condition.isLessThan(
            orEqual = false,
            variable = "long",
            number = (long + 10).toString()
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun lessThan_Matches_If_Int_Less_Than_Nested_Value() {
        val condition = Condition.isLessThan(
            orEqual = false,
            path = listOf("object"),
            variable = "key",
            number = "346"
        )
        assertTrue(condition.matches(payload))
    }
}