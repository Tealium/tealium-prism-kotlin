package com.tealium.prism.core.internal.rules

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.data.ReferenceContainer.Companion.path
import com.tealium.prism.core.api.data.get
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.ConditionEvaluationException
import com.tealium.prism.core.api.rules.MissingDataItemException
import com.tealium.prism.core.api.rules.MissingFilterException
import com.tealium.prism.core.api.rules.UnsupportedOperatorException
import com.tealium.tests.common.assertThrows
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
    fun doesNotStartWith_Matches_Different_Int() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "int",
            prefix = "12"
        )
        assertTrue(condition.matches(payload))
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
    fun doesNotStartWith_Matches_Different_Double() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "double",
            prefix = "5.5"
        )
        assertTrue(condition.matches(payload))
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
    fun doesNotStartWith_Matches_Different_Bool() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "bool",
            prefix = "fa"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Does_Not_Match_Nested_Value() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = JsonPath["object"]["key"],
            prefix = "Val"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Does_Not_Match_Stringified_Array() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "list",
            prefix = "a,1"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Does_Not_Match_Stringified_Array_Containing_Simplified_Doubles() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "list",
            prefix = "1,2.2,"
        )
        assertFalse(condition.matches(doublesPayload))
    }

    @Test
    fun doesNotStartWithIgnoreCase_Does_Not_Match_Stringified_Array_Containing_Simplified_Doubles() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = true,
            variable = "list",
            prefix = "1,2.2,3.3"
        )
        assertFalse(condition.matches(doublesPayload))
    }

    @Test
    fun doesNotStartWith_Matches_Json_Array_String() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "list",
            prefix = "[\"a\""
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotStartWithIgnoreCase_Does_Not_Match_Stringified_Array() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = true,
            variable = "list",
            prefix = "A,1"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Does_Not_Match_Nested_Value_Ignoring_Case() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = true,
            variable = JsonPath["object"]["key"],
            prefix = "vAl"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotStartWith_Throws_When_Filter_Null() {
        val condition = Condition(
            variable = path(JsonPath["object"]["key"]),
            operator = Operators.doesNotStartWith,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotStartWith_Throws_When_DataItem_Missing() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = JsonPath["object"]["missing"],
            prefix = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotStartWithIgnoreCase_Throws_When_Filter_Null() {
        val condition = Condition(
            variable = path(JsonPath["object"]["key"]),
            operator = Operators.doesNotStartWithIgnoreCase,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotStartWithIgnoreCase_Throws_When_DataItem_Missing() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = true,
            variable = JsonPath["object"]["missing"],
            prefix = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotStartWith_Throws_When_DataItem_Is_A_DataObject() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "object",
            prefix = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotStartWithIgnoreCase_Throws_When_DataItem_Is_A_DataObject() {
        val condition = Condition.doesNotStartWith(
            ignoreCase = true,
            variable = "object",
            prefix = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotStartWith_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.doesNotStartWith(
            ignoreCase = false,
            variable = "list",
            prefix = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotStartWithIgnoreCase_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.doesNotStartWith(
            ignoreCase = true,
            variable = "list",
            prefix = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }
}