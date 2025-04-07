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
class ConditionsEndsWithTests {

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
    fun endsWith_Matches_String() {
        val condition = Condition.endsWith(
            ignoreCase = false,
            variable = "string",
            suffix = "alue"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun endsWith_Does_Not_Match_Different_String() {
        val condition = Condition.endsWith(
            ignoreCase = false,
            variable = "string",
            suffix = "something_else"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun endsWith_Does_Not_Match_String_With_Different_Casing() {
        val condition = Condition.endsWith(
            ignoreCase = false,
            variable = "string",
            suffix = "AlUe"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun endsWith_Matches_String_Ignoring_Case() {
        val condition = Condition.endsWith(
            ignoreCase = true,
            variable = "string",
            suffix = "ALUE"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun endsWith_Matches_Int() {
        val condition = Condition.endsWith(
            ignoreCase = false,
            variable = "int",
            suffix = "45"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun endsWith_Matches_Double() {
        val condition = Condition.endsWith(
            ignoreCase = false,
            variable = "double",
            suffix = ".14"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun endsWith_Matches_Bool() {
        val condition = Condition.endsWith(
            ignoreCase = false,
            variable = "bool",
            suffix = "ue"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun endsWith_Matches_Nested_Value() {
        val condition = Condition.endsWith(
            ignoreCase = false,
            path = listOf("object"),
            variable = "key",
            suffix = "alue"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun endsWith_Matches_Array() {
        val condition = Condition.endsWith(
            ignoreCase = false,
            variable = "list",
            suffix = "c\"]"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun endsWith_Matches_Nested_Value_Ignoring_Case() {
        val condition = Condition.endsWith(
            ignoreCase = true,
            path = listOf("object"),
            variable = "key",
            suffix = "ALUE"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun endsWith_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.endsWith,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun endsWith_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.endsWith(
            ignoreCase = false,
            path = listOf("object"),
            variable = "missing",
            suffix = "value"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun endsWithIgnoreCase_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.endsWithIgnoreCase,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun endsWithIgnoreCase_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.endsWith(
            ignoreCase = true,
            path = listOf("object"),
            variable = "missing",
            suffix = "value"
        )
        assertFalse(condition.matches(payload))
    }
}