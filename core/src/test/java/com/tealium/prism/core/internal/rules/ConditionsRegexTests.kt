package com.tealium.prism.core.internal.rules

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.data.ReferenceContainer.Companion.key
import com.tealium.prism.core.api.data.ReferenceContainer.Companion.path
import com.tealium.prism.core.api.data.get
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.ConditionEvaluationException
import com.tealium.prism.core.api.rules.MissingDataItemException
import com.tealium.prism.core.api.rules.MissingFilterException
import com.tealium.prism.core.api.rules.UnsupportedOperatorException
import com.tealium.prism.core.api.data.StringContainer
import com.tealium.tests.common.assertThrows
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
        put("list", DataList.create {
            add("a")
            add(1)
            add(false)
            add(DataList.create { add("b"); add(2); add(true) })
        })
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
            variable = JsonPath["object"]["key"],
            regex = "Val"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun regularExpression_Matches_Stringified_Array() {
        val condition = Condition.regularExpression(
            variable = "list",
            regex = "a,1,false,b,2,true"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun regularExpression_Matches_Stringified_Array_Ignoring_Case() {
        val condition = Condition.regularExpression(
            variable = "list",
            regex = "(?i)A,1,false,b,2,TRUE"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun regularExpression_Matches_Nested_Value_Ignoring_Case() {
        val condition = Condition.regularExpression(
            variable = JsonPath["object"]["key"],
            regex = "(?i)vAl"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun regularExpression_Matches_When_Filter_Is_Null_String() {
        val condition = Condition(
            variable = key("null"),
            operator = Operators.regularExpression,
            filter = StringContainer("null")
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun regularExpression_Throws_When_Filter_Null() {
        val condition = Condition(
            variable = path(JsonPath["object"]["key"]),
            operator = Operators.regularExpression,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun regularExpression_Throws_When_DataItem_Missing() {
        val condition = Condition.regularExpression(
            variable = JsonPath["object"]["missing"],
            regex = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun regularExpression_Throws_When_DataItem_Is_DataObject() {
        val condition = Condition.regularExpression(
            variable = "object",
            regex = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun regularExpression_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.regularExpression(
            variable = "list",
            regex = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }
}