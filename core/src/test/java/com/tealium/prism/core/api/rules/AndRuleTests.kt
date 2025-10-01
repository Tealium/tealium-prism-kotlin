package com.tealium.prism.core.api.rules

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.rules.Rule.Companion.just
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndRuleTests {

    @Test
    fun all_Matches_True_If_No_Rules() {
        val rule: Rule<Matchable<DataObject>> = Rule.all()

        assertTrue(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun all_Matches_True_If_Just_Rule_Matches_True() {
        val rule: Rule<Matchable<DataObject>> = Rule.all(
            just(Matchable { true })
        )

        assertTrue(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun all_Matches_False_If_Just_Rule_Matches_False() {
        val rule: Rule<Matchable<DataObject>> = Rule.all(
            just(Matchable { false })
        )

        assertFalse(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun all_Matches_True_If_All_Rules_Match_True() {
        val rule: Rule<Matchable<DataObject>> = Rule.all(
            just(Matchable { true }),
            just(Matchable { true })
        )

        assertTrue(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun all_Matches_False_If_Any_Rules_Match_False() {
        val rule: Rule<Matchable<DataObject>> = Rule.all(
            just(Matchable { true }),
            just(Matchable { false })
        )

        assertFalse(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun all_Matches_True_If_All_Nested_Rules_Match_True() {
        val rule: Rule<Matchable<DataObject>> = Rule.all(
            just(Matchable { true }),
            Rule.all(
                just(Matchable { true }),
                just(Matchable { true }),
            )
        )

        assertTrue(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun all_Matches_False_If_Any_Nested_Rules_Match_False() {
        val rule: Rule<Matchable<DataObject>> = Rule.all(
            just(Matchable { true }),
            Rule.all(
                just(Matchable { true }),
                just(Matchable { false }),
            )
        )

        assertFalse(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun all_Does_Not_Query_Other_Rules_After_First_False() {
        val mockMatchable = mockk<Matchable<DataObject>>()
        every { mockMatchable.matches(any()) } returns true

        val rule: Rule<Matchable<DataObject>> = Rule.all(
            just(Matchable { false }),
            just(mockMatchable)
        )

        assertFalse(rule.matches(DataObject.EMPTY_OBJECT))

        verify {
            mockMatchable wasNot Called
        }
    }

    @Test
    fun and_Matches_False_If_Any_Rule_Matches_False() {
        val rule: Rule<Matchable<DataObject>> = Rule.just<Matchable<DataObject>>(Matchable { true })
            .and(just(Matchable { false }))

        assertFalse(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun and_Matches_True_If_All_Rules_Do() {
        val rule: Rule<Matchable<DataObject>> = Rule.just<Matchable<DataObject>>(Matchable { true })
            .and(just(Matchable { true }))
            .and(just(Matchable { true }))
            .and(just(Matchable { true }))

        assertTrue(rule.matches(DataObject.EMPTY_OBJECT))
    }
}