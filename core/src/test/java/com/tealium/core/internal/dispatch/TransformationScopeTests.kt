package com.tealium.core.internal.dispatch

import com.tealium.core.api.transformations.DispatchScope
import com.tealium.core.api.transformations.TransformationScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformationScopeTests {

    @Test
    fun matches_ReturnsTrue_When_BothAreAfterCollectors() {
        assertTrue(TransformationScope.AfterCollectors.matches(DispatchScope.AfterCollectors))
    }

    @Test
    fun matches_ReturnsFalse_When_AfterCollectors_ButDispatchScope_IsNot() {
        assertFalse(TransformationScope.AfterCollectors.matches(DispatchScope.Dispatcher("")))
    }

    @Test
    fun matches_ReturnsTrue_When_AllDispatchers_AndDispatchScope_IsDispatcher() {
        assertTrue(TransformationScope.AllDispatchers.matches(DispatchScope.Dispatcher("dispatcher_1")))
        assertTrue(TransformationScope.AllDispatchers.matches(DispatchScope.Dispatcher("dispatcher_2")))
    }

    @Test
    fun matches_ReturnsFalse_When_AllDispatchers_AndDispatchScope_IsAfterCollectors() {
        assertFalse(TransformationScope.AllDispatchers.matches(DispatchScope.AfterCollectors))
    }

    @Test
    fun matches_ReturnsTrue_When_SpecificDispatcher_AndDispatchScope_IsDispatcherWithMatchingId() {
        assertTrue(
            TransformationScope.Dispatcher("dispatcher_1")
                .matches(DispatchScope.Dispatcher("dispatcher_1"))
        )
    }

    @Test
    fun matches_ReturnsFalse_When_SpecificDispatcher_AndDispatchScope_DoesNotMatchDispatcherId() {
        assertFalse(
            TransformationScope.Dispatcher("dispatcher_1")
                .matches(DispatchScope.Dispatcher("different"))
        )
    }
}