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
class ConditionsNotEndsWithTests {

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
    fun doesNotEndWith_Does_Not_Match_String() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "string",
            suffix = "alue"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Matches_Different_String() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "string",
            suffix = "something_else"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Matches_String_With_Different_Casing() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "string",
            suffix = "AlUe"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Does_Not_Match_String_Ignoring_Case() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = true,
            variable = "string",
            suffix = "ALUE"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Does_Not_Match_Ending_Int() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "int",
            suffix = "45"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Does_Not_Match_Ending_Double() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "double",
            suffix = ".14"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Does_Not_Match_Ending_Bool() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "bool",
            suffix = "ue"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Does_Not_Match_Nested_Value() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            path = listOf("object"),
            variable = "key",
            suffix = "alue"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Does_Not_Match_Array() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "list",
            suffix = "c\"]"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWithIgnoreCase_Does_Not_Match_Nested_Value_Ignoring_Case() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = true,
            path = listOf("object"),
            variable = "key",
            suffix = "ALUE"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.doesNotEndWith,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            path = listOf("object"),
            variable = "missing",
            suffix = "value"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWithIgnoreCase_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.doesNotEndWithIgnoreCase,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWithIgnoreCase_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = true,
            path = listOf("object"),
            variable = "missing",
            suffix = "value"
        )
        assertFalse(condition.matches(payload))
    }
}