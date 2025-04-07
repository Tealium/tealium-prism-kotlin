package com.tealium.core.api.rules

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Rule.Companion.just
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrRuleTests {

    @Test
    fun any_Matches_False_If_No_Rules() {
        val rule: Rule<Matchable<DataObject>> = Rule.any()

        assertFalse(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun any_Matches_True_If_Just_Rule_Matches_True() {
        val rule: Rule<Matchable<DataObject>> = Rule.any(
            just(Matchable { true })
        )

        assertTrue(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun any_Matches_False_If_Just_Rule_Matches_False() {
        val rule: Rule<Matchable<DataObject>> = Rule.any(
            just(Matchable { false })
        )

        assertFalse(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun any_Matches_True_If_Any_Rules_Match_True() {
        val rule: Rule<Matchable<DataObject>> = Rule.any(
            just(Matchable { true }),
            just(Matchable { false }),
            just(Matchable { false })
        )

        assertTrue(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun any_Matches_False_If_All_Rules_Match_False() {
        val rule: Rule<Matchable<DataObject>> = Rule.any(
            just(Matchable { false }),
            just(Matchable { false })
        )

        assertFalse(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun any_Matches_False_If_All_Nested_Rules_Match_False() {
        val rule: Rule<Matchable<DataObject>> = Rule.any(
            just(Matchable { false }),
            just(Matchable { false }),
            Rule.any(
                just(Matchable { false }),
                just(Matchable { false }),
            )
        )

        assertFalse(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun any_Matches_True_If_Any_Nested_Rules_Match_True() {
        val rule: Rule<Matchable<DataObject>> = Rule.any(
            just(Matchable { false }),
            Rule.any(
                just(Matchable { false }),
                just(Matchable { true }),
            )
        )

        assertTrue(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun any_Does_Not_Query_Other_Rules_After_First_True() {
        val mockMatchable = mockk<Matchable<DataObject>>()
        every { mockMatchable.matches(any()) } returns false

        val rule: Rule<Matchable<DataObject>> = Rule.any(
            just(Matchable { true }),
            just(mockMatchable)
        )

        assertTrue(rule.matches(DataObject.EMPTY_OBJECT))

        verify {
            mockMatchable wasNot Called
        }
    }

    @Test
    fun or_Matches_False_If_All_Rules_Match_False() {
        val rule: Rule<Matchable<DataObject>> = Rule.just<Matchable<DataObject>>(Matchable { false })
            .or(just(Matchable { false }))

        assertFalse(rule.matches(DataObject.EMPTY_OBJECT))
    }

    @Test
    fun or_Matches_True_If_Any_Rules_Do() {
        val rule: Rule<Matchable<DataObject>> = Rule.just<Matchable<DataObject>>(Matchable { false })
            .or(just(Matchable { false }))
            .or(just(Matchable { false }))
            .or(just(Matchable { true }))

        assertTrue(rule.matches(DataObject.EMPTY_OBJECT))
    }
}