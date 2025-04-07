package com.tealium.core.api.rules

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Rule.Companion.just
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotRuleTests {

    @Test
    fun not_Returns_True_If_Inner_Rule_Is_False() {
        val rule: Rule<Matchable<DataObject>> = Rule.not(
            just(Matchable { false })
        )

        assertTrue(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun inline_not_Returns_False_If_Inner_Rule_Is_True() {
        val rule = Rule.just<Matchable<DataObject>>(
            Matchable { true }
        ).not()

        assertFalse(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun not_Returns_False_If_Inner_Rule_Is_True() {
        val rule: Rule<Matchable<DataObject>> = Rule.not(
            Rule.just(Matchable { true })
        )

        assertFalse(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun inline_not_Returns_True_If_Inner_Rule_Is_False() {
        val rule = Rule.just<Matchable<DataObject>>(
            Matchable { false }
        ).not()

        assertTrue(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun notNot_Returns_Underlying_Result() {
        val trueRule = Rule.just<Matchable<DataObject>>(
            Matchable { true }
        ).not().not()
        val falseRule = Rule.just<Matchable<DataObject>>(
            Matchable { false }
        ).not().not()

        assertTrue(trueRule.matches(DataObject.EMPTY_OBJECT))
        assertFalse(falseRule.matches(DataObject.EMPTY_OBJECT))
    }
}