package com.tealium.core.internal.dispatch

import com.tealium.core.api.transformations.DispatchScope
import com.tealium.core.api.transformations.ScopedTransformation
import com.tealium.core.api.transformations.TransformationScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopedTransformationTests {

    @Test
    fun matchesScope_ReturnsTrue_When_ScopeContains_AfterCollectors_And_DispatchScope_IsAfterCollectors() {
        val st = ScopedTransformation(
            "", "", setOf(
                TransformationScope.AfterCollectors
            )
        )

        assertTrue(st.matchesScope(DispatchScope.AfterCollectors))
    }

    @Test
    fun matchesScope_ReturnsFalse_When_ScopeContains_AfterCollectors_And_DispatchScope_IsNotAfterCollectors() {
        val st = ScopedTransformation(
            "", "", setOf(
                TransformationScope.AfterCollectors
            )
        )

        assertFalse(st.matchesScope(DispatchScope.Dispatcher("")))
    }

    @Test
    fun matchesScope_ReturnsTrue_When_ScopeContains_AllDispatchers_And_DispatchScope_IsAnyDispatcher() {
        val st = ScopedTransformation(
            "", "", setOf(
                TransformationScope.AllDispatchers
            )
        )

        assertTrue(st.matchesScope(DispatchScope.Dispatcher("")))
    }

    @Test
    fun matchesScope_ReturnsFalse_When_ScopeContains_AllCollectors_And_DispatchScope_IsAfterCollectors() {
        val st = ScopedTransformation(
            "", "", setOf(
                TransformationScope.AllDispatchers
            )
        )

        assertFalse(st.matchesScope(DispatchScope.AfterCollectors))
    }

    @Test
    fun matchesScope_ReturnsTrue_When_ScopeContains_SpecificDispatchers_And_DispatchScope_IsDispatcherWithMatchingId() {
        val st = ScopedTransformation(
            "", "", setOf(
                TransformationScope.Dispatcher("dispatcher_1"),
                TransformationScope.Dispatcher("dispatcher_2")
            )
        )

        assertTrue(st.matchesScope(DispatchScope.Dispatcher("dispatcher_1")))
        assertTrue(st.matchesScope(DispatchScope.Dispatcher("dispatcher_2")))
    }

    @Test
    fun matchesScope_ReturnsFalse_When_ScopeContains_SpecificDispatchers_And_DispatchScope_IsAfterCollectors() {
        val st = ScopedTransformation(
            "", "", setOf(
                TransformationScope.Dispatcher("dispatcher_1"),
                TransformationScope.Dispatcher("dispatcher_2")
            )
        )

        assertFalse(st.matchesScope(DispatchScope.AfterCollectors))
    }

    @Test
    fun matchesScope_ReturnsFalse_When_ScopeContains_SpecificDispatchers_And_DispatchScope_IsDispatcher_ButWithNonMatchingId() {
        val st = ScopedTransformation(
            "", "", setOf(
                TransformationScope.Dispatcher("dispatcher_1"),
                TransformationScope.Dispatcher("dispatcher_2")
            )
        )

        assertFalse(st.matchesScope(DispatchScope.Dispatcher("dispatcher_3")))
    }

    @Test
    fun matchesScope_ReturnsTrue_When_ScopeContains_MultipleScopes_And_DispatchScope_OneThatMatches() {
        val st = ScopedTransformation(
            "", "", setOf(
                TransformationScope.Dispatcher("dispatcher_1"),
                TransformationScope.AfterCollectors
            )
        )

        assertTrue(st.matchesScope(DispatchScope.Dispatcher("dispatcher_1")))
        assertTrue(st.matchesScope(DispatchScope.AfterCollectors))
    }
}