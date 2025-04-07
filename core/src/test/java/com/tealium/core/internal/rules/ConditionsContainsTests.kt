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
class ConditionsContainsTests {

    private val payload = DataObject.create {
        put("string", "Value")
        put("int", 345)
        put("double", 3.14)
        put("bool", true)
        put("list", DataList.create { add("a"); add("b"); add("c") })
        put("object", DataObject.Builder().put("key", "Value").build())
        put("null", DataItem.NULL)
    }

    @Test
    fun contains_Matches_String() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "string",
            string = "al"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Does_Not_Match_Different_String() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "string",
            string = "something_else"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun contains_Does_Not_Match_String_With_Different_Casing() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "string",
            string = "Al"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun containsIgnoreCase_Matches_String_Ignoring_Case() {
        val condition = Condition.contains(
            ignoreCase = true,
            variable = "string",
            string = "AL"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Matches_Int() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "int",
            string = "4"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Matches_Double() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "double",
            string = ".1"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Matches_Bool() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "bool",
            string = "tr"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Matches_Nested_Value() {
        val condition = Condition.contains(
            ignoreCase = false,
            path = listOf("object"),
            variable = "key",
            string = "al"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Matches_Array() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "list",
            string = "a"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun containsIgnoreCase_Matches_Nested_Value_Ignoring_Case() {
        val condition = Condition.contains(
            ignoreCase = true,
            path = listOf("object"),
            variable = "key",
            string = "AL"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.containsIgnoreCase,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun contains_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.contains(
            ignoreCase = false,
            path = listOf("object"),
            variable = "missing",
            string = "value"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun containsIgnoreCase_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.containsIgnoreCase,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun containsIgnoreCase_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.contains(
            ignoreCase = true,
            path = listOf("object"),
            variable = "missing",
            string = "value"
        )
        assertFalse(condition.matches(payload))
    }
}