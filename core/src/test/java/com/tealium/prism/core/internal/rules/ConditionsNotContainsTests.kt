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
class ConditionsNotContainsTests {

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
    fun doesNotContain_Matches_Different_Int() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = "int",
            string = "14"
        )
        assertTrue(condition.matches(payload))
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
    fun doesNotContain_Matches_Different_Double() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = "double",
            string = ".5"
        )
        assertTrue(condition.matches(payload))
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
    fun doesNotContain_Matches_Different_Bool() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = "bool",
            string = "fa"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Does_Not_Match_Contained_Nested_Value() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = JsonPath["object"]["key"],
            string = "al"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Does_Not_Match_Stringified_Array_Containing_Value() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = "list",
            string = "a"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Matches_Stringified_Array_Not_Containing_Simplified_Doubles() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = "list",
            string = "1.0"
        )
        assertTrue(condition.matches(doublesPayload))
    }

    @Test
    fun doesNotContainIgnoreCase_Matches_Stringified_Array_Not_Containing_Simplified_Doubles() {
        val condition = Condition.doesNotContain(
            ignoreCase = true,
            variable = "list",
            string = "1.0"
        )
        assertTrue(condition.matches(doublesPayload))
    }

    @Test
    fun doesNotContain_Matches_Json_Stringified_Array() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = "list",
            string = "[\"a\",1,false"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotContainIgnoreCase_Does_Not_Match_Contained_Nested_Value_Ignoring_Case() {
        val condition = Condition.doesNotContain(
            ignoreCase = true,
            variable = JsonPath["object"]["key"],
            string = "AL"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Does_Not_Match_When_Null_String() {
        val condition = Condition(
            variable = key("null"),
            operator = Operators.doesNotContain,
            filter = StringContainer("null")
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContain_Throws_When_Filter_Null() {
        val condition = Condition(
            variable = path(JsonPath["object"]["key"]),
            operator = Operators.doesNotContain,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotContain_Throws_When_DataItem_Missing() {
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = JsonPath["object"]["missing"],
            string = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotContainIgnoreCase_Does_Not_Match_When_Null_String() {
        val condition = Condition(
            variable = key("null"),
            operator = Operators.doesNotContainIgnoreCase,
            filter = StringContainer("null")
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotContainIgnoreCase_Throws_When_Filter_Null() {
        val condition = Condition(
            variable = path(JsonPath["object"]["key"]),
            operator = Operators.doesNotContainIgnoreCase,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotContainIgnoreCase_Throws_When_DataItem_Missing() {
        val condition = Condition.doesNotContain(
            ignoreCase = true,
            variable = JsonPath["object"]["missing"],
            string = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotContainIgnoreCase_Throws_When_DataItem_Is_DataObject() {
        val condition = Condition.doesNotContain(
            ignoreCase = true,
            variable = "object",
            string = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotContain_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.doesNotContain(
            ignoreCase = false,
            variable = "list",
            string = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotContainIgnoreCase_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.doesNotContain(
            ignoreCase = true,
            variable = "list",
            string = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }
}