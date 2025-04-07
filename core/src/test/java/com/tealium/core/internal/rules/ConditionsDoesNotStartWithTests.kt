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
class ConditionsDoesNotStartWithTests {

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
    fun doesNotStartWith_Does_Not_Match_String() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "string",
            prefix = "Val"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Matches_Different_String() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "string",
            prefix = "something_else"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Matches_String_With_Different_Casing() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "string",
            prefix = "val"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Does_Not_Match_String_Ignoring_Case() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = true,
            variable = "string",
            prefix = "vAl"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Does_Not_Match_Int() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "int",
            prefix = "3"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Does_Not_Match_Double() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "double",
            prefix = "3.1"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Does_Not_Match_Bool() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "bool",
            prefix = "tr"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Does_Not_Match_Nested_Value() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            path = listOf("object"),
            variable = "key",
            prefix = "Val"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Does_Not_Match_Array() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "list",
            prefix = "[\"a"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Does_Not_Match_Nested_Value_Ignoring_Case() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = true,
            path = listOf("object"),
            variable = "key",
            prefix = "vAl"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.doesNotStartWith,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            path = listOf("object"),
            variable = "missing",
            prefix = "value"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWithIgnoreCase_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.doesNotStartWithIgnoreCase,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWithIgnoreCase_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = true,
            path = listOf("object"),
            variable = "missing",
            prefix = "value"
        )
        assertFalse(condition.matches(payload))
    }
}