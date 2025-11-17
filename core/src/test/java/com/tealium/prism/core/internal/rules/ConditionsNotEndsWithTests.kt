package com.tealium.prism.core.internal.rules

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.data.ReferenceContainer.Companion.key
import com.tealium.prism.core.api.data.get
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.ConditionEvaluationException
import com.tealium.prism.core.api.rules.MissingDataItemException
import com.tealium.prism.core.api.rules.MissingFilterException
import com.tealium.prism.core.api.rules.UnsupportedOperatorException
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.tests.common.assertThrows
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
        put("list", DataList.create {
            add("a")
            add(1)
            add(false)
            add(DataList.create { add("b"); add(2); add(true) })
        })
        put("object", DataObject.Builder().put("key", "Value").build())
        put("null", DataItem.NULL)
    }
    private val doublesPayload = DataObject.create {
        put("list", DataList.create {
            add(1.000)
            add(2.200)
            add(3.333)
        })
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
    fun doesNotEndWithIgnoreCase_Does_Not_Match_String_Ignoring_Case() {
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
    fun doesNotEndWith_Matches_Different_Int() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "int",
            suffix = "46"
        )
        assertTrue(condition.matches(payload))
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
    fun doesNotEndWith_Matches_Different_Double() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "double",
            suffix = ".15"
        )
        assertTrue(condition.matches(payload))
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
    fun doesNotEndWith_Matches_Different_Bool() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "bool",
            suffix = "se"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Does_Not_Match_Nested_Value() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = JsonPath["object"]["key"],
            suffix = "alue"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Does_Not_Match_Stringified_Array() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "list",
            suffix = "2,true"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Matches_Stringified_Array_Not_Containing_Simplified_Doubles() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "list",
            suffix = "3.3"
        )
        assertTrue(condition.matches(doublesPayload))
    }

    @Test
    fun doesNotEndWithIgnoreCase_Matches_Stringified_Array_Not_Containing_Simplified_Doubles() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = true,
            variable = "list",
            suffix = "3.3"
        )
        assertTrue(condition.matches(doublesPayload))
    }

    @Test
    fun doesNotEndWith_Does_Not_Match_Stringified_Array_Containing_Simplified_Doubles() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "list",
            suffix = "3.333"
        )
        assertFalse(condition.matches(doublesPayload))
    }

    @Test
    fun doesNotEndWithIgnoreCase_Does_Not_Match_Stringified_Array_Containing_Simplified_Doubles() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = true,
            variable = "list",
            suffix = "3.333"
        )
        assertFalse(condition.matches(doublesPayload))
    }

    @Test
    fun doesNotEndWith_Matches_Json_Stringified_Array() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "list",
            suffix = "\"b\",2,true]"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEndWithIgnoreCase_Does_Not_Match_Stringified_Array() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = true,
            variable = "list",
            suffix = "b,2,TRUE"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWithIgnoreCase_Matches_Json_Stringified_Array() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = true,
            variable = "list",
            suffix = "\"b\",2,true]"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEndWithIgnoreCase_Does_Not_Match_Nested_Value_Ignoring_Case() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = true,
            variable = JsonPath["object"]["key"],
            suffix = "ALUE"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Does_Not_Match_When_Filter_Is_Null_String() {
        val condition = Condition(
            variable = key("null"),
            operator = Operators.doesNotEndWith,
            filter = ValueContainer("null")
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEndWith_Throws_When_Filter_Null() {
        val condition = Condition(
            variable = key("string"),
            operator = Operators.doesNotEndWith,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEndWith_Throws_When_DataItem_Missing() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = JsonPath["object"]["missing"],
            suffix = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEndWithIgnoreCase_Throws_When_Filter_Null() {
        val condition = Condition(
            variable = key("string"),
            operator = Operators.doesNotEndWithIgnoreCase,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEndWithIgnoreCase_Does_Not_Match_When_DataItem_Missing() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = true,
            variable = JsonPath["object"]["missing"],
            suffix = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEndWith_Throws_When_DataItem_Is_DataObject() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "object",
            suffix = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEndWithIgnoreCase_Throws_When_DataItem_Is_DataObject() {
        val condition = Condition.doesNotEndWith(
            ignoreCase = true,
            variable = "object",
            suffix = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEndWith_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.doesNotEndWith(
            ignoreCase = false,
            variable = "list",
            suffix = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEndWithIgnoreCase_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.doesNotEndWith(
            ignoreCase = true,
            variable = "list",
            suffix = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }
}