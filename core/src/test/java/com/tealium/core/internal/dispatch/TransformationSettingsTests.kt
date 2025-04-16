package com.tealium.core.internal.dispatch

import com.tealium.core.api.transform.DispatchScope
import com.tealium.core.api.transform.TransformationSettings
import com.tealium.core.api.transform.TransformationScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformationSettingsTests {

    @Test
    fun matchesScope_ReturnsTrue_When_ScopeContains_AfterCollectors_And_DispatchScope_IsAfterCollectors() {
        val ts = TransformationSettings(
            "", "", setOf(
                TransformationScope.AfterCollectors
            )
        )

        assertTrue(ts.matchesScope(DispatchScope.AfterCollectors))
    }

    @Test
    fun matchesScope_ReturnsFalse_When_ScopeContains_AfterCollectors_And_DispatchScope_IsNotAfterCollectors() {
        val ts = TransformationSettings(
            "", "", setOf(
                TransformationScope.AfterCollectors
            )
        )

        assertFalse(ts.matchesScope(DispatchScope.Dispatcher("")))
    }

    @Test
    fun matchesScope_ReturnsTrue_When_ScopeContains_AllDispatchers_And_DispatchScope_IsAnyDispatcher() {
        val ts = TransformationSettings(
            "", "", setOf(
                TransformationScope.AllDispatchers
            )
        )

        assertTrue(ts.matchesScope(DispatchScope.Dispatcher("")))
    }

    @Test
    fun matchesScope_ReturnsFalse_When_ScopeContains_AllCollectors_And_DispatchScope_IsAfterCollectors() {
        val ts = TransformationSettings(
            "", "", setOf(
                TransformationScope.AllDispatchers
            )
        )

        assertFalse(ts.matchesScope(DispatchScope.AfterCollectors))
    }

    @Test
    fun matchesScope_ReturnsTrue_When_ScopeContains_SpecificDispatchers_And_DispatchScope_IsDispatcherWithMatchingId() {
        val ts = TransformationSettings(
            "", "", setOf(
                TransformationScope.Dispatcher("dispatcher_1"),
                TransformationScope.Dispatcher("dispatcher_2")
            )
        )

        assertTrue(ts.matchesScope(DispatchScope.Dispatcher("dispatcher_1")))
        assertTrue(ts.matchesScope(DispatchScope.Dispatcher("dispatcher_2")))
    }

    @Test
    fun matchesScope_ReturnsFalse_When_ScopeContains_SpecificDispatchers_And_DispatchScope_IsAfterCollectors() {
        val ts = TransformationSettings(
            "", "", setOf(
                TransformationScope.Dispatcher("dispatcher_1"),
                TransformationScope.Dispatcher("dispatcher_2")
            )
        )

        assertFalse(ts.matchesScope(DispatchScope.AfterCollectors))
    }

    @Test
    fun matchesScope_ReturnsFalse_When_ScopeContains_SpecificDispatchers_And_DispatchScope_IsDispatcher_ButWithNonMatchingId() {
        val ts = TransformationSettings(
            "", "", setOf(
                TransformationScope.Dispatcher("dispatcher_1"),
                TransformationScope.Dispatcher("dispatcher_2")
            )
        )

        assertFalse(ts.matchesScope(DispatchScope.Dispatcher("dispatcher_3")))
    }

    @Test
    fun matchesScope_ReturnsTrue_When_ScopeContains_MultipleScopes_And_DispatchScope_OneThatMatches() {
        val ts = TransformationSettings(
            "", "", setOf(
                TransformationScope.Dispatcher("dispatcher_1"),
                TransformationScope.AfterCollectors
            )
        )

        assertTrue(ts.matchesScope(DispatchScope.Dispatcher("dispatcher_1")))
        assertTrue(ts.matchesScope(DispatchScope.AfterCollectors))
    }
}