package com.tealium.core.api.rules

import com.tealium.core.internal.rules.Operators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConditionTests {

    @Test
    fun isEqual_Returns_Equals_Operator_When_Ignore_Case_False() {
        val condition = Condition.isEqual(false, variable = "key", target = "target")
        assertEquals(Operators.equals, condition.operator)
    }

    @Test
    fun isEqual_Returns_EqualsIgnoreCase_Operator_When_Ignore_Case_True() {
        val condition = Condition.isEqual(true, variable = "key", target = "target")
        assertEquals(Operators.equalsIgnoreCase, condition.operator)
    }

    @Test
    fun isEqual_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.isEqual(true, listOf("obj1", "obj2"), "key", "target")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals("target", condition.filter)
    }

    @Test
    fun doesNotEqual_Returns_DoesNotEqual_Operator_When_Ignore_Case_False() {
        val condition = Condition.doesNotEqual(false, variable = "key", target = "target")
        assertEquals(Operators.doesNotEqual, condition.operator)
    }

    @Test
    fun doesNotEqual_Returns_DoesNotEqualIgnoreCase_Operator_When_Ignore_Case_True() {
        val condition = Condition.doesNotEqual(true, variable = "key", target = "target")
        assertEquals(Operators.doesNotEqualIgnoreCase, condition.operator)
    }

    @Test
    fun doesNotEqual_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.doesNotEqual(true, listOf("obj1", "obj2"), "key", "target")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals("target", condition.filter)
    }

    @Test
    fun contains_Returns_Contains_Operator_When_Ignore_Case_False() {
        val condition = Condition.contains(false, variable = "key", string = "target")
        assertEquals(Operators.contains, condition.operator)
    }

    @Test
    fun contains_Returns_ContainsIgnoreCase_Operator_When_Ignore_Case_True() {
        val condition = Condition.contains(true, variable = "key", string = "target")
        assertEquals(Operators.containsIgnoreCase, condition.operator)
    }

    @Test
    fun contains_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.contains(true, listOf("obj1", "obj2"), "key", "target")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals("target", condition.filter)
    }

    @Test
    fun doesNotContain_Returns_DoesNotContain_Operator_When_Ignore_Case_False() {
        val condition = Condition.doesNotContain(false, variable = "key", string = "target")
        assertEquals(Operators.doesNotContain, condition.operator)
    }

    @Test
    fun doesNotContain_Returns_DoesNotContainIgnoreCase_Operator_When_Ignore_Case_True() {
        val condition = Condition.doesNotContain(true, variable = "key", string = "target")
        assertEquals(Operators.doesNotContainIgnoreCase, condition.operator)
    }

    @Test
    fun doesNotContain_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.doesNotContain(true, listOf("obj1", "obj2"), "key", "target")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals("target", condition.filter)
    }

    @Test
    fun startsWith_Returns_StartsWith_Operator_When_Ignore_Case_False() {
        val condition = Condition.startsWith(false, variable = "key", prefix = "target")
        assertEquals(Operators.startsWith, condition.operator)
    }

    @Test
    fun startsWith_Returns_StartsWithIgnoreCase_Operator_When_Ignore_Case_True() {
        val condition = Condition.startsWith(true, variable = "key", prefix = "target")
        assertEquals(Operators.startsWithIgnoreCase, condition.operator)
    }

    @Test
    fun startsWith_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.startsWith(true, listOf("obj1", "obj2"), "key", "target")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals("target", condition.filter)
    }

    @Test
    fun doesNotStartWith_Returns_DoesNotStartWith_Operator_When_Ignore_Case_False() {
        val condition = Condition.doesNotStartWith(false, variable = "key", prefix = "target")
        assertEquals(Operators.doesNotStartWith, condition.operator)
    }

    @Test
    fun doesNotStartWith_Returns_DoesNotStartWithIgnoreCase_Operator_When_Ignore_Case_True() {
        val condition = Condition.doesNotStartWith(true, variable = "key", prefix = "target")
        assertEquals(Operators.doesNotStartWithIgnoreCase, condition.operator)
    }

    @Test
    fun doesNotStartWith_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.doesNotStartWith(true, listOf("obj1", "obj2"), "key", "target")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals("target", condition.filter)
    }

    @Test
    fun endsWith_Returns_EndsWith_Operator_When_Ignore_Case_False() {
        val condition = Condition.endsWith(false, variable = "key", suffix = "target")
        assertEquals(Operators.endsWith, condition.operator)
    }

    @Test
    fun endsWith_Returns_EndsWithIgnoreCase_Operator_When_Ignore_Case_True() {
        val condition = Condition.endsWith(true, variable = "key", suffix = "target")
        assertEquals(Operators.endsWithIgnoreCase, condition.operator)
    }

    @Test
    fun endsWith_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.endsWith(true, listOf("obj1", "obj2"), "key", "target")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals("target", condition.filter)
    }

    @Test
    fun doesNotEndWith_Returns_DoesNotEndWith_Operator_When_Ignore_Case_False() {
        val condition = Condition.doesNotEndWith(false, variable = "key", suffix = "target")
        assertEquals(Operators.doesNotEndWith, condition.operator)
    }

    @Test
    fun doesNotEndWith_Returns_DoesNotEndWithIgnoreCase_Operator_When_Ignore_Case_True() {
        val condition = Condition.doesNotEndWith(true, variable = "key", suffix = "target")
        assertEquals(Operators.doesNotEndWithIgnoreCase, condition.operator)
    }

    @Test
    fun doesNotEndWith_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.doesNotEndWith(true, listOf("obj1", "obj2"), "key", "target")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals("target", condition.filter)
    }

    @Test
    fun isDefined_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.isDefined(listOf("obj1", "obj2"), "key")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals(Operators.isDefined, condition.operator)
        assertNull(condition.filter)
    }

    @Test
    fun isNotDefined_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.isNotDefined(listOf("obj1", "obj2"), "key")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals(Operators.isNotDefined, condition.operator)
        assertNull(condition.filter)
    }

    @Test
    fun isNotEmpty_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.isNotEmpty(listOf("obj1", "obj2"), "key")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals(Operators.isNotEmpty, condition.operator)
        assertNull(condition.filter)
    }

    @Test
    fun isEmpty_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.isEmpty(listOf("obj1", "obj2"), "key")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals(Operators.isEmpty, condition.operator)
        assertNull(condition.filter)
    }

    @Test
    fun isGreaterThan_Returns_GreaterThan_Operator_When_Or_Equals_False() {
        val condition = Condition.isGreaterThan(false, variable = "key", number = "target")
        assertEquals(Operators.greaterThan, condition.operator)
    }

    @Test
    fun isGreaterThan_Returns_GreaterThanOrEquals_Operator_When_Or_Equals_True() {
        val condition = Condition.isGreaterThan(true, variable = "key", number = "target")
        assertEquals(Operators.greaterThanOrEquals, condition.operator)
    }

    @Test
    fun isGreaterThan_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.isGreaterThan(true, listOf("obj1", "obj2"), "key", "target")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals("target", condition.filter)
    }

    @Test
    fun isLessThan_Returns_LessThan_Operator_When_Or_Equals_False() {
        val condition = Condition.isLessThan(false, variable = "key", number = "target")
        assertEquals(Operators.lessThan, condition.operator)
    }

    @Test
    fun isLessThan_Returns_LessThanOrEquals_Operator_When_Or_Equals_True() {
        val condition = Condition.isLessThan(true, variable = "key", number = "target")
        assertEquals(Operators.lessThanOrEquals, condition.operator)
    }

    @Test
    fun isLessThan_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.isLessThan(true, listOf("obj1", "obj2"), "key", "target")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals("target", condition.filter)
    }

    @Test
    fun regularExpression_Returns_Condition_With_Provided_Parameters() {
        val condition =
            Condition.regularExpression(listOf("obj1", "obj2"), "key", "target")
        assertEquals(listOf("obj1", "obj2"), condition.path)
        assertEquals("key", condition.variable)
        assertEquals(Operators.regularExpression, condition.operator)
        assertEquals("target", condition.filter)
    }
}