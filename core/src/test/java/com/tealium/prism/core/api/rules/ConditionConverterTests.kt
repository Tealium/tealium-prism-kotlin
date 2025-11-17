package com.tealium.prism.core.api.rules

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.get
import com.tealium.prism.core.api.rules.Condition.Converter.KEY_FILTER
import com.tealium.prism.core.api.rules.Condition.Converter.KEY_OPERATOR
import com.tealium.prism.core.api.rules.Condition.Converter.KEY_VARIABLE
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.internal.rules.Operators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConditionConverterTests {

    @Test
    fun convert_Converts_Variable_From_Key_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "equals")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(JsonPath["key"], condition.variable.path)
    }

    @Test
    fun convert_Converts_Variable_From_Path_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "equals")
            put(KEY_VARIABLE, path("key.property"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(JsonPath["key"]["property"], condition.variable.path)
    }

    @Test
    fun convert_Returns_Null_When_Variable_Omitted() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "equals")
        }.asDataItem()

        val condition = Condition.Converter.convert(item)

        assertNull(condition)
    }

    @Test
    fun convert_Converts_Filter_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "equals")
            put(KEY_VARIABLE, key("key"))
            put(KEY_FILTER, value("value"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals("value", condition.filter?.value)
    }

    @Test
    fun convert_Set_Filter_To_Null_When_Omitted() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "equals")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertNull(condition.filter)
    }

    @Test
    fun convert_Converts_Equals_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "equals")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.equals, condition.operator)
    }

    @Test
    fun convert_Converts_EqualsIgnoreCase_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "equals_ignore_case")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.equalsIgnoreCase, condition.operator)
    }

    @Test
    fun convert_Converts_DoesNotEqual_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "does_not_equal")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.doesNotEqual, condition.operator)
    }

    @Test
    fun convert_Converts_DoesNotEqualIgnoreCase_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "does_not_equal_ignore_case")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.doesNotEqualIgnoreCase, condition.operator)
    }

    @Test
    fun convert_Converts_StartsWith_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "starts_with")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.startsWith, condition.operator)
    }

    @Test
    fun convert_Converts_StartsWithIgnoreCase_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "starts_with_ignore_case")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.startsWithIgnoreCase, condition.operator)
    }

    @Test
    fun convert_Converts_DoesNotStartWith_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "does_not_start_with")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.doesNotStartWith, condition.operator)
    }

    @Test
    fun convert_Converts_DoseNotStartWithIgnoreCase_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "does_not_start_with_ignore_case")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.doesNotStartWithIgnoreCase, condition.operator)
    }

    @Test
    fun convert_Converts_EndsWith_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "ends_with")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.endsWith, condition.operator)
    }

    @Test
    fun convert_Converts_EndsWithIgnoreCase_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "ends_with_ignore_case")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.endsWithIgnoreCase, condition.operator)
    }

    @Test
    fun convert_Converts_DoesNotEndWith_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "does_not_end_with")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.doesNotEndWith, condition.operator)
    }

    @Test
    fun convert_Converts_DoseNotEndWithIgnoreCase_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "does_not_end_with_ignore_case")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.doesNotEndWithIgnoreCase, condition.operator)
    }

    @Test
    fun convert_Converts_Contains_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "contains")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.contains, condition.operator)
    }

    @Test
    fun convert_Converts_ContainsIgnoreCase_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "contains_ignore_case")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.containsIgnoreCase, condition.operator)
    }

    @Test
    fun convert_Converts_DoesNotContains_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "does_not_contain")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.doesNotContain, condition.operator)
    }

    @Test
    fun convert_Converts_DoseNotContainIgnoreCase_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "does_not_contain_ignore_case")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.doesNotContainIgnoreCase, condition.operator)
    }

    @Test
    fun convert_Converts_IsDefined_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "defined")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.isDefined, condition.operator)
    }

    @Test
    fun convert_Converts_IsNotDefined_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "notdefined")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.isNotDefined, condition.operator)
    }

    @Test
    fun convert_Converts_IsNotEmpty_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "notempty")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.isNotEmpty, condition.operator)
    }

    @Test
    fun convert_Converts_IsEmpty_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "empty")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.isEmpty, condition.operator)
    }

    @Test
    fun convert_Converts_GreaterThan_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "greater_than")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.greaterThan, condition.operator)
    }

    @Test
    fun convert_Converts_GreaterThanOrEquals_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "greater_than_equal_to")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.greaterThanOrEquals, condition.operator)
    }

    @Test
    fun convert_Converts_LessThan_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "less_than")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.lessThan, condition.operator)
    }

    @Test
    fun convert_Converts_LessThanOrEquals_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "less_than_equal_to")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.lessThanOrEquals, condition.operator)
    }

    @Test
    fun convert_Converts_RegularExpression_Operator_When_Present() {
        val item = DataObject.create {
            put(KEY_OPERATOR, "regular_expression")
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)!!

        assertEquals(Operators.regularExpression, condition.operator)
    }

    @Test
    fun convert_Returns_Null_When_Operator_Omitted() {
        val item = DataObject.create {
            put(KEY_VARIABLE, key("key"))
        }.asDataItem()

        val condition = Condition.Converter.convert(item)

        assertNull(condition)
    }

    private fun key(key: String): DataObject =
        DataObject.create {
            put(ReferenceContainer.Converter.KEY_KEY, key)
        }

    private fun path(path: String): DataObject =
        DataObject.create {
            put(ReferenceContainer.Converter.KEY_PATH, path)
        }

    private fun value(value: String): DataObject =
        DataObject.create {
            put(ValueContainer.Converter.KEY_VALUE, value)
        }
}