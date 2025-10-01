package com.tealium.prism.core.internal.rules

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.ConditionEvaluationException
import com.tealium.prism.core.api.rules.MissingDataItemException
import com.tealium.prism.core.api.rules.MissingFilterException
import com.tealium.prism.core.api.rules.NumberParseException
import com.tealium.tests.common.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConditionsGreaterThanOrEqualsTests {

    private val payload = DataObject.create {
        put("string", "Value")
        put("numberstring", "45")
        put("int", 345)
        put("double", 3.14)
        put("bool", true)
        put("list", DataList.create {
            add("a")
            add(1)
            add(false)
            add(DataList.create { add("b"); add(2); add(true) })
        })
        put("object", DataObject.Builder().put("key", "Value").put("int", 345).build())
        put("null", DataItem.NULL)
        put("infinity", Double.POSITIVE_INFINITY)
        put("negativeinfinity", Double.NEGATIVE_INFINITY)
        put("nan", Double.NaN)
        put("emptystring", "")
    }

    @Test
    fun greaterThanOrEquals_Does_Not_Match_String() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "string",
            number = "Value"
        )
        assertThrows<ConditionEvaluationException>(cause = NumberParseException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun greaterThanOrEquals_Matches_Number_String() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "numberstring",
            number = "10"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEquals_Matches_Number_String_When_Numbers_Are_Equal() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "numberstring",
            number = "45"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEquals_Does_Not_Match_Number_String_When_Target_Is_Greater() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "numberstring",
            number = "50"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEquals_Throws_When_DataItem_Is_Boolean() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "bool",
            number = "true"
        )
        assertThrows<ConditionEvaluationException>(cause = NumberParseException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun greaterThanOrEquals_Throws_When_DataItem_Is_DataArray() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "list",
            number = "10"
        )
        assertThrows<ConditionEvaluationException>(cause = NumberParseException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun greaterThanOrEquals_Throws_When_DataItem_Is_DataObject() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "object",
            number = "10"
        )
        assertThrows<ConditionEvaluationException>(cause = NumberParseException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun greaterThanOrEquals_Throws_When_DataItem_Is_Null() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "null",
            number = "10"
        )
        assertThrows<ConditionEvaluationException>(cause = NumberParseException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun greaterThan_Throws_When_Filter_Is_Null() {
        val condition = Condition(
            operator = Operators.greaterThanOrEquals,
            variable = "int",
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun greaterThanOrEquals_Matches_If_Int_Greater_Than_Filter() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "int",
            number = "344"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEquals_Matches_If_Double_Greater_Than_Filter() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "double",
            number = "3.13"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEquals_Matches_If_Long_Greater_Than_Filter() {
        val payload = payload.copy {
            put("long", Int.MAX_VALUE.toLong() + 100)
        }
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "long",
            number = Int.MAX_VALUE.toString()
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEquals_Matches_If_Int_Equals_Filter() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "int",
            number = "345"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEquals_Matches_If_Double_Equals_Filter() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "double",
            number = "3.14"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEquals_Matches_If_Long_Equals_Filter() {
        val long = Int.MAX_VALUE.toLong() + 100
        val payload = payload.copy {
            put("long", long)
        }
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "long",
            number = long.toString()
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEquals_Does_Not_Match_If_Int_Less_Than_Filter() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "int",
            number = "346"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEquals_Does_Not_Match_If_Double_Less_Than_Filter() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "double",
            number = "3.2"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEquals_Does_Not_Match_If_Long_Less_Than_Filter() {
        val long = Int.MAX_VALUE.toLong() + 100
        val payload = payload.copy {
            put("long", long)
        }
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "long",
            number = (long + 10).toString()
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEquals_Matches_If_Int_Greater_Than_Nested_Value() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "int",
            number = "344"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEqual_Matches_Infinity() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "infinity",
            number = "10"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEqual_Matches_Infinity_Equality() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "infinity",
            number = "Infinity"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEqual_Does_Not_Match_Negative_Infinity() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "negativeinfinity",
            number = "10"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEqual_Matches_Negative_Infinity_Equality() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "negativeinfinity",
            number = "-Infinity"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEqual_Does_Not_Match_When_DataItem_Is_NaN() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "nan",
            number = "0"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEqual_Does_Not_Match_When_Filter_Is_NaN() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "infinity",
            number = "NaN"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEqual_Does_Not_Match_Two_NaNs() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "nan",
            number = "NaN"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun greaterThanOrEqual_Throws_When_DataItem_Is_Empty_String() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "emptystring",
            number = "-99"
        )
        assertThrows<ConditionEvaluationException>(cause = NumberParseException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun greaterThanOrEqual_Throws_When_Filter_Is_Empty_String() {
        val condition = Condition.isGreaterThan(
            orEqual = true,
            variable = "infinity",
            number = ""
        )
        assertThrows<ConditionEvaluationException>(cause = NumberParseException::class) {
            condition.matches(payload)
        }
    }
}