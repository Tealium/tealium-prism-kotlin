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
import com.tealium.prism.core.api.data.StringContainer
import com.tealium.tests.common.assertThrows
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
    fun startsWithIgnoreCase_Matches_String_Ignoring_Case() {
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
    fun startsWith_Does_Not_Match_Different_Int() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "int",
            prefix = "5"
        )
        assertFalse(condition.matches(payload))
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
    fun startsWith_Does_Not_Match_Different_Double() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "double",
            prefix = "4.1"
        )
        assertFalse(condition.matches(payload))
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
    fun startsWith_Does_Not_Match_Different_Bool() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "bool",
            prefix = "fa"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun startsWith_Matches_Nested_Value() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = JsonPath["object"]["key"],
            prefix = "Val"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWith_Matches_Stringified_Array() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "list",
            prefix = "a,1,false,b"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWithIgnoreCase_Matches_Stringified_Array() {
        val condition = Condition.startsWith(
            ignoreCase = true,
            variable = "list",
            prefix = "A,1,fALse,B"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWith_Matches_Stringified_Array_Containing_Simplified_Doubles() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "list",
            prefix = "1,2.2"
        )
        assertTrue(condition.matches(doublesPayload))
    }

    @Test
    fun startsWithIgnoreCase_Matches_Stringified_Array_Containing_Simplified_Doubles() {
        val condition = Condition.startsWith(
            ignoreCase = true,
            variable = "list",
            prefix = "1,2.2"
        )
        assertTrue(condition.matches(doublesPayload))
    }

    @Test
    fun startsWithIgnoreCase_Matches_Nested_Value_Ignoring_Case() {
        val condition = Condition.startsWith(
            ignoreCase = true,
            variable = JsonPath["object"]["key"],
            prefix = "vAl"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWith_Matches_When_Filter_Is_Null_String() {
        val condition = Condition(
            variable = key("null"),
            operator = Operators.startsWith,
            filter = StringContainer("null")
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWith_Throws_When_Filter_Null() {
        val condition = Condition(
            variable = key("string"),
            operator = Operators.startsWith,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun startsWith_Throws_When_DataItem_Missing() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = JsonPath["object"]["missing"],
            prefix = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun startsWithIgnoreCase_Matches_When_Filter_Is_Null_String() {
        val condition = Condition(
            variable = key("null"),
            operator = Operators.startsWithIgnoreCase,
            filter = StringContainer("null")
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun startsWithIgnoreCase_Throws_When_Filter_Null() {
        val condition = Condition(
            variable = key("string"),
            operator = Operators.startsWithIgnoreCase,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun startsWithIgnoreCase_Throws_When_DataItem_Missing() {
        val condition = Condition.startsWith(
            ignoreCase = true,
            variable = JsonPath["object"]["missing"],
            prefix = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun startsWith_Throws_When_DataItem_Is_DataObject() {
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "object",
            prefix = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun startsWithIgnoreCase_Throws_When_DataItem_Is_DataObject() {
        val condition = Condition.startsWith(
            ignoreCase = true,
            variable = "object",
            prefix = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun startsWith_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.startsWith(
            ignoreCase = false,
            variable = "list",
            prefix = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun startsWithIgnoreCase_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.startsWith(
            ignoreCase = true,
            variable = "list",
            prefix = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }
}