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
class ConditionsEqualsTests {

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
    fun equals_Matches_String() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "string",
            target = "Value"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equals_Does_Not_Match_Different_String() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "string",
            target = "something_else"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun equals_Does_Not_Match_String_With_Different_Casing() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "string",
            target = "vAlUe"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun equalsIgnoreCase_Matches_String_Ignoring_Case() {
        val condition = Condition.isEqual(
            ignoreCase = true,
            variable = "string",
            target = "vAlUe"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equals_Matches_Int() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "int",
            target = "345"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equals_Matches_Double() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "double",
            target = "3.14"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equals_Matches_Bool() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "bool",
            target = "true"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equals_Matches_Nested_Value() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            path = listOf("object"),
            variable = "key",
            target = "Value"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equals_Matches_Array() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "list",
            target = list.toString()
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equalsIgnoreCase_Matches_Nested_Value_Ignoring_Case() {
        val condition = Condition.isEqual(
            ignoreCase = true,
            path = listOf("object"),
            variable = "key",
            target = "vAlUe"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equals_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.equals,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun equals_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            path = listOf("object"),
            variable = "missing",
            target = "value"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun equalsIgnoreCase_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.equalsIgnoreCase,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun equalsIgnoreCase_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.isEqual(
            ignoreCase = true,
            path = listOf("object"),
            variable = "missing",
            target = "value"
        )
        assertFalse(condition.matches(payload))
    }
}