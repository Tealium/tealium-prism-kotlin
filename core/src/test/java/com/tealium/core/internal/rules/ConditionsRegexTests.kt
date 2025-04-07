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
class ConditionsRegexTests {

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
    fun regularExpression_Matches_String() {
        val condition = Condition.regularExpression(
            variable = "string",
            regex = "Val"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun regularExpression_Does_Not_Match_Different_String() {
        val condition = Condition.regularExpression(
            variable = "string",
            regex = "something_else"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun regularExpression_Does_Not_Match_String_With_Different_Casing() {
        val condition = Condition.regularExpression(
            variable = "string",
            regex = "val"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun regularExpression_Matches_String_Ignoring_Case() {
        val condition = Condition.regularExpression(
            variable = "string",
            regex = "(?i)vAl"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun regularExpression_Matches_Int() {
        val condition = Condition.regularExpression(
            variable = "int",
            regex = "3"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun regularExpression_Matches_Double() {
        val condition = Condition.regularExpression(
            variable = "double",
            regex = ".1"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun regularExpression_Matches_Bool() {
        val condition = Condition.regularExpression(
            variable = "bool",
            regex = "tr"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun regularExpression_Matches_Nested_Value() {
        val condition = Condition.regularExpression(
            path = listOf("object"),
            variable = "key",
            regex = "Val"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun regularExpression_Matches_Array() {
        val condition = Condition.regularExpression(
            variable = "list",
            regex = "\\[\"a"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun regularExpression_Matches_Nested_Value_Ignoring_Case() {
        val condition = Condition.regularExpression(
            path = listOf("object"),
            variable = "key",
            regex = "(?i)vAl"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun regularExpression_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.regularExpression,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun regularExpression_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.regularExpression(
            path = listOf("object"),
            variable = "missing",
            regex = "value"
        )
        assertFalse(condition.matches(payload))
    }
}