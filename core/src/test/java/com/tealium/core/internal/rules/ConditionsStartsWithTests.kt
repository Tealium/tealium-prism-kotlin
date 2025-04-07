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
class ConditionsStartsWithTests {

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
    fun startsWith_Matches_String() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "string",
            prefix = "Val"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWith_Does_Not_Match_Different_String() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "string",
            prefix = "something_else"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun startsWith_Does_Not_Match_String_With_Different_Casing() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "string",
            prefix = "val"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun startsWith_Matches_String_Ignoring_Case() {
        val condition = Condition.startsWith(
            ignoreCase = true,
            variable = "string",
            prefix = "vAl"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWith_Matches_Int() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "int",
            prefix = "3"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWith_Matches_Double() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "double",
            prefix = "3.1"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWith_Matches_Bool() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "bool",
            prefix = "tr"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWith_Matches_Nested_Value() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            path = listOf("object"),
            variable = "key",
            prefix = "Val"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWith_Matches_Array() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "list",
            prefix = "[\"a"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWithIgnoreCase_Matches_Nested_Value_Ignoring_Case() {
        val condition = Condition.startsWith(
            ignoreCase = true,
            path = listOf("object"),
            variable = "key",
            prefix = "vAl"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWith_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.startsWith,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun startsWith_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            path = listOf("object"),
            variable = "missing",
            prefix = "value"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun startsWithIgnoreCase_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.startsWithIgnoreCase,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun startsWithIgnoreCase_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.startsWith(
            ignoreCase = true,
            path = listOf("object"),
            variable = "missing",
            prefix = "value"
        )
        assertFalse(condition.matches(payload))
    }
}