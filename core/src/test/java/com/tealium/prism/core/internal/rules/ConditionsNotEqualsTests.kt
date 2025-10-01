package com.tealium.prism.core.internal.rules

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
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
class ConditionsNotEqualsTests {

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
        put("infinity", Double.POSITIVE_INFINITY)
        put("negativeinfinity", Double.NEGATIVE_INFINITY)
        put("nan", Double.NaN)
        put("emptystring", "")
    }

    @Test
    fun doesNotEqual_Does_Not_Match_String() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "string",
            target = "Value"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_Different_String() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "string",
            target = "something_else"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_String_With_Different_Casing() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "string",
            target = "vAlUe"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqualIgnoreCase_Does_Not_Match_String_Ignoring_Case() {
        val condition = Condition.doesNotEqual(
            ignoreCase = true,
            variable = "string",
            target = "vAlUe"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Equal_Int() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "int",
            target = "345"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_Different_Int() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "int",
            target = "346"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_Different_Type_Of_Number() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "int",
            target = "1.1"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Double() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "double",
            target = "3.14"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_Different_Double() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "double",
            target = "3.2"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_Different_Bool() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "bool",
            target = "false"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Bool() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "bool",
            target = "true"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Nested_Value() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            path = listOf("object"),
            variable = "key",
            target = "Value"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Stringified_Array() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "list",
            target = "a,1,false,b,2,true"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_Different_Array() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "list",
            target = DataList.EMPTY_LIST.toString()
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Matches_Json_Formatted_Array() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "list",
            target = "[\"a\",1,false,\"b\",2,true]"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun doesNotEqualIgnoreCase_Does_Not_Match_Nested_Value_Ignoring_Case() {
        val condition = Condition.doesNotEqual(
            ignoreCase = true,
            path = listOf("object"),
            variable = "key",
            target = "vAlUe"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_When_Filter_Is_Null_String() {
        val condition = Condition(
            variable = "null",
            operator = Operators.doesNotEqual,
            filter = "null"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Throws_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.doesNotEqual,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEqual_Throws_When_DataItem_Missing() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            path = listOf("object"),
            variable = "missing",
            target = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEqualIgnoreCase_Does_Not_Match_When_Filter_Is_Null_String() {
        val condition = Condition(
            variable = "null",
            operator = Operators.doesNotEqualIgnoreCase,
            filter = "null"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqualIgnoreCase_Throws_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.doesNotEqualIgnoreCase,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEqualIgnoreCase_Throws_When_DataItem_Missing() {
        val condition = Condition.doesNotEqual(
            ignoreCase = true,
            path = listOf("object"),
            variable = "missing",
            target = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEqual_Throws_When_DataItem_Is_DataObject() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "object",
            target = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEqualIgnoreCase_Throws_When_DataItem_Is_DataObject() {
        val condition = Condition.doesNotEqual(
            ignoreCase = true,
            variable = "object",
            target = "value"
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEqual_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "list",
            target = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEqualIgnoreCase_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.doesNotEqual(
            ignoreCase = true,
            variable = "list",
            target = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Infinity_String() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "infinity",
            target = "Infinity"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Negative_Infinity_String() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "negativeinfinity",
            target = "-Infinity"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_NaN_String() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "nan",
            target = "NaN"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqualIgnoreCase_Does_Not_Match_Mixed_Case_Infinity_String() {
        val condition = Condition.doesNotEqual(
            ignoreCase = true,
            variable = "infinity",
            target = "inFINity"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqualIgnoreCase_Does_Not_Match_Mixed_Case_Negative_Infinity_String() {
        val condition = Condition.doesNotEqual(
            ignoreCase = true,
            variable = "negativeinfinity",
            target = "-inFINity"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqualIgnoreCase_Does_Not_Match_Mixed_Case_NaN_String() {
        val condition = Condition.doesNotEqual(
            ignoreCase = true,
            variable = "nan",
            target = "nAn"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Int_Filter_When_Double_Value_Has_No_Fraction() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "zeroFractionDouble",
            target = "1"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Int_String_When_Double_Value_Has_No_Fraction_In_DataList() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add("a")
                add(1.0)
                add(true)
            })
        }
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "list",
            target = "a,1,true"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqualIgnoreCase_Does_Not_Match_Int_Filter_When_Double_Value_Has_No_Fraction() {
        val condition = Condition.doesNotEqual(
            ignoreCase = true,
            variable = "zeroFractionDouble",
            target = "1"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqualIgnoreCase_Does_Not_Match_Int_String_When_Double_Value_Has_No_Fraction_In_DataList() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add("a")
                add(1.0)
                add(true)
            })
        }
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "list",
            target = "a,1,true"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun doesNotEqual_Does_Not_Match_Matching_Large_Doubles() {
        val condition = Condition.doesNotEqual(
            ignoreCase = false,
            variable = "bigDouble",
            target = "12345678901234567890.0"
        )
        assertFalse(condition.matches(payload))
    }
}