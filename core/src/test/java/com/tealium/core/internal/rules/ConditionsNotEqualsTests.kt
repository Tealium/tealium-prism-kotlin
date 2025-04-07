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
class ConditionsNotEqualsTests {

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
    fun doesNotEqual_Does_Not_Match_String() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "string",
            target = "Value"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_Different_String() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "string",
            target = "something_else"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_String_With_Different_Casing() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "string",
            target = "vAlUe"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqualIgnoreCase_Does_Not_Match_String_Ignoring_Case() {
        val condition = Condition.doesNotEqual(
            ignoreCase = true,
            variable = "string",
            target = "vAlUe"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Equal_Int() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "int",
            target = "345"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_Different_Int() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "int",
            target = "346"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_Different_Type_Of_Number() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "int",
            target = "1.1"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Double() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "double",
            target = "3.14"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_Different_Double() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "double",
            target = "3.2"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_Different_Bool() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "bool",
            target = "false"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Bool() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "bool",
            target = "true"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Nested_Value() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            path = listOf("object"),
            variable = "key",
            target = "Value"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Array() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "list",
            target = list.toString()
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_Different_Array() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "list",
            target = DataList.EMPTY_LIST.toString()
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqualIgnoreCase_Does_Not_Match_Nested_Value_Ignoring_Case() {
        val condition = Condition.doesNotEqual(
            ignoreCase = true,
            path = listOf("object"),
            variable = "key",
            target = "vAlUe"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.doesNotEqual,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            path = listOf("object"),
            variable = "missing",
            target = "value"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqualIgnoreCase_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.doesNotEqualIgnoreCase,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqualIgnoreCase_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.doesNotEqual(
            ignoreCase = true,
            path = listOf("object"),
            variable = "missing",
            target = "value"
        )
        assertFalse(condition.matches(payload))
    }
}