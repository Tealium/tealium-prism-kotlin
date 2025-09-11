package com.tealium.core.internal.rules

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Condition
import com.tealium.core.api.rules.ConditionEvaluationException
import com.tealium.core.api.rules.MissingDataItemException
import com.tealium.core.api.rules.MissingFilterException
import com.tealium.core.api.rules.UnsupportedOperatorException
import com.tealium.tests.common.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConditionsEqualsTests {

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
    fun equals_Does_Not_Match_Different_Int() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "int",
            target = "555"
        )
        assertFalse(condition.matches(payload))
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
    fun equals_Does_Not_Match_Different_Double() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "double",
            target = "5.55"
        )
        assertFalse(condition.matches(payload))
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
    fun equals_Does_Not_Match_Different_Bool() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "bool",
            target = "false"
        )
        assertFalse(condition.matches(payload))
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
    fun equals_Matches_Stringified_Array() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "list",
            target = "a,1,false,b,2,true"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equals_Does_Not_Match_Different_Stringified_Array() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "list",
            target = "a,1,false"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun equals_Does_Not_Match_Json_Array_String() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "list",
            target = "[\"a\",1,false,[\"b\",2,false]]"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun equalsIgnoreCase_Matches_Stringified_Array() {
        val condition = Condition.isEqual(
            ignoreCase = true,
            variable = "list",
            target = "A,1,fALse,B,2,True"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equalsIgnoreCase_Does_Not_Match_Different_Stringified_Array() {
        val condition = Condition.isEqual(
            ignoreCase = true,
            variable = "list",
            target = "A,1,FALSE"
        )
        assertFalse(condition.matches(payload))
    }

    @Test
    fun equalsIgnoreCase_Does_Not_Match_Json_Array_String() {
        val condition = Condition.isEqual(
            ignoreCase = true,
            variable = "list",
            target = "[\"a\",1,false,[\"b\",2,false]]"
        )
        assertFalse(condition.matches(payload))
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
    fun equals_Throws_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.equals,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun equals_Throws_When_DataItem_Missing() {
        val condition = Condition.isEqual(
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
    fun equalsIgnoreCase_Throws_When_Filter_Null() {
        val condition = Condition(
            path = listOf("object"),
            variable = "key",
            operator = Operators.equalsIgnoreCase,
            filter = null
        )
        assertThrows<ConditionEvaluationException>(cause = MissingFilterException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun equalsIgnoreCase_Throws_When_DataItem_Missing() {
        val condition = Condition.isEqual(
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
    fun equals_Throws_When_DataItem_Is_A_DataObject() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "object",
            target = "{\"key\":\"Value\"}"
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun equalsIgnoreCase_Throws_When_DataItem_Is_A_DataObject() {
        val condition = Condition.isEqual(
            ignoreCase = true,
            variable = "object",
            target = "{\"key\":\"Value\"}"
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun equals_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "list",
            target = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun equalsIgnoreCase_Throws_When_DataItem_Is_A_DataList_Containing_A_DataObject() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add(DataObject.EMPTY_OBJECT)
            })
        }
        val condition = Condition.isEqual(
            ignoreCase = true,
            variable = "list",
            target = "{\"key\""
        )
        assertThrows<ConditionEvaluationException>(cause = UnsupportedOperatorException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun equals_Matches_Infinity_String() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "infinity",
            target = "Infinity"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equals_Matches_Negative_Infinity_String() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "negativeinfinity",
            target = "-Infinity"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equals_Matches_NaN_String() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "nan",
            target = "NaN"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equalsIgnoreCase_Matches_Mixed_Case_Infinity_String() {
        val condition = Condition.isEqual(
            ignoreCase = true,
            variable = "infinity",
            target = "inFINity"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equalsIgnoreCase_Matches_Mixed_Case_Negative_Infinity_String() {
        val condition = Condition.isEqual(
            ignoreCase = true,
            variable = "negativeinfinity",
            target = "-inFINity"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equalsIgnoreCase_Matches_Mixed_Case_NaN_String() {
        val condition = Condition.isEqual(
            ignoreCase = true,
            variable = "nan",
            target = "nAn"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equals_Matches_Int_Filter_When_Double_Value_Has_No_Fraction() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "zeroFractionDouble",
            target = "1"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equals_Matches_Int_String_When_Double_Value_Has_No_Fraction_In_DataList() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add("a")
                add(1.0)
                add(true)
            })
        }
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "list",
            target = "a,1,true"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equalsIgnoreCase_Matches_Int_Filter_When_Double_Value_Has_No_Fraction() {
        val condition = Condition.isEqual(
            ignoreCase = true,
            variable = "zeroFractionDouble",
            target = "1"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equalsIgnoreCase_Matches_Int_String_When_Double_Value_Has_No_Fraction_In_DataList() {
        val payload = DataObject.create {
            put("list", DataList.create {
                add("a")
                add(1.0)
                add(true)
            })
        }
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "list",
            target = "a,1,true"
        )
        assertTrue(condition.matches(payload))
    }

    @Test
    fun equals_Matches_Doubles_In_Non_Scientific_Form() {
        val condition = Condition.isEqual(
            ignoreCase = false,
            variable = "bigDouble",
            target = "12345678901234567890.0"
        )
        assertTrue(condition.matches(payload))
    }
}