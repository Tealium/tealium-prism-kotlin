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
class ConditionsNotContainsTests {

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
    fun doesNotContain_Does_Not_Match_String() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = "string",
            string = "al"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Matches_Different_String() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = "string",
            string = "something_else"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Matches_String_With_Different_Casing() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = "string",
            string = "Al"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotContainIgnoreCase_Does_Not_Match_String_Ignoring_Case() {
        val condition = Condition.doesNotContain(
            ignoreCase = true,
            variable = "string",
            string = "AL"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Does_Not_Match_Contained_Int() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = "int",
            string = "4"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Does_Not_Match_Contained_Double() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = "double",
            string = ".1"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Does_Not_Match_Contained_Bool() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = "bool",
            string = "tr"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Does_Not_Match_Contained_Nested_Value() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            path = listOf("object"),
            variable = "key",
            string = "al"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Does_Not_Match_Contained_Array() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            null,
            variable = "list",
            string = "a"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContainIgnoreCase_Does_Not_Match_Contained_Nested_Value_Ignoring_Case() {
        val condition = Condition.doesNotContain(
            ignoreCase = true,
            path = listOf("object"),
            variable = "key",
            string = "AL"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.doesNotContain,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            path = listOf("object"),
            variable = "missing",
            string = "value"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContainIgnoreCase_Does_Not_Match_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.doesNotContainIgnoreCase,
            filter = null
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContainIgnoreCase_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.doesNotContain(
            ignoreCase = true,
            path = listOf("object"),
            variable = "missing",
            string = "value"
        )
        assertFalse(condition.matches(payload))
    }
}