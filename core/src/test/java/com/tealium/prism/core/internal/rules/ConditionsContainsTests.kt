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
class ConditionsContainsTests {

    private val payload = DataObject.create {
        put("string", "Value")
        put("int", 345)
        put("double", 3.14)
        put("bigDouble", 12_345_678_901_234_567_890.0)
        put("zeroFractionDouble", 1.0)
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
    fun contains_Matches_String() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "string",
            string = "al"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Does_Not_Match_Different_String() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "string",
            string = "something_else"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun contains_Does_Not_Match_String_With_Different_Casing() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "string",
            string = "Al"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun containsIgnoreCase_Matches_String_Ignoring_Case() {
        val condition = Condition.contains(
            ignoreCase = true,
            variable = "string",
            string = "AL"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Matches_Int() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "int",
            string = "4"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Matches_Double() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "double",
            string = ".1"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Matches_Bool() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "bool",
            string = "tr"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Matches_Nested_Value() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = JsonPath["object"]["key"],
            string = "al"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Matches_Stringified_Array() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "list",
            string = "alse,b,2,tr"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun containsIgnoreCase_Matches_Stringified_Array() {
        val condition = Condition.contains(
            ignoreCase = true,
            variable = "list",
            string = "Alse,B,2,TR"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Matches_Stringified_Array_Containing_Simplified_Doubles() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "list",
            string = "1,2.2,"
        )
        assertTrue(condition.matches(doublesPayload))
    }

    @Test
    fun containsIgnoreCase_Matches_Stringified_Array_Containing_Simplified_Doubles() {
        val condition = Condition.contains(
            ignoreCase = true,
            variable = "list",
            string = "1,2.2,3.3"
        )
        assertTrue(condition.matches(doublesPayload))
    }

    @Test
    fun containsIgnoreCase_Does_Not_Match_Stringified_Array_Containing_Simplified_Doubles_With_Higher_Precision() {
        val condition = Condition.contains(
            ignoreCase = true,
            variable = "list",
            string = "3.3333333"
        )
        assertFalse(condition.matches(doublesPayload))
    }

    @Test
    fun containsIgnoreCase_Matches_Nested_Value_Ignoring_Case() {
        val condition = Condition.contains(
            ignoreCase = true,
            variable = JsonPath["object"]["key"],
            string = "AL"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Matches_Null_String() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "null",
            string = "null"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun contains_Throws_When_Filter_Null() {
        val condition = Condition(
            variable = path(JsonPath["object"]["key"]),
            operator = Operators.contains,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun contains_Throws_When_DataItem_Missing() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = JsonPath["object"]["missing"],
            string = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun contains_Throws_When_DataItem_Is_A_DataObject() {
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "object",
            string = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun containsIgnoreCase_Matches_Null_String() {
        val condition = Condition.contains(
            ignoreCase = true,
            variable = "null",
            string = "NUl"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun containsIgnoreCase_Throws_When_Filter_Null() {
        val condition = Condition(
            variable = path(JsonPath["object"]["key"]),
            operator = Operators.containsIgnoreCase,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun containsIgnoreCase_Throws_When_DataItem_Missing() {
        val condition = Condition.contains(
            ignoreCase = true,
            variable = JsonPath["object"]["missing"],
            string = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun containsIgnoreCase_Throws_When_DataItem_Is_A_DataObject() {
        val condition = Condition.contains(
            ignoreCase = true,
            variable = "object",
            string = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun contains_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.contains(
            ignoreCase = false,
            variable = "list",
            string = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun containsIgnoreCase_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.contains(
            ignoreCase = true,
            variable = "list",
            string = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }
}