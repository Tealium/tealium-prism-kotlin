package com.tealium.prism.core.ktx

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StateExtensionsTest {

    private lateinit var subject: StateSubject<String>

    @Before
    fun setUp() {
        subject = Observables.stateSubject("initial")
    }

    // region asStateFlow()

    @Test
    fun asStateFlow_Returns_Current_Value_Immediately() = runTest(UnconfinedTestDispatcher()) {
        val stateFlow: StateFlow<String> = subject.asStateFlow(this)

        assertEquals("initial", stateFlow.value)
    }

    @Test
    fun asStateFlow_Updates_On_New_Emissions() = runTest(UnconfinedTestDispatcher()) {
        val stateFlow: StateFlow<String> = subject.asStateFlow(this)

        subject.onNext("second")
        assertEquals("second", stateFlow.value)

        subject.onNext("third")
        assertEquals("third", stateFlow.value)
    }

    @Test
    fun asStateFlow_Retains_Last_Value_After_Upstream_Completes() = runTest(UnconfinedTestDispatcher()) {
        val stateFlow: StateFlow<String> = subject.asStateFlow(this)

        subject.onNext("final")
        subject.onComplete()

        // StateFlow should still hold the last emitted value.
        assertEquals("final", stateFlow.value)
    }

    @Test
    fun asStateFlow_Disposes_Subscription_When_Scope_Cancelled() = runTest(UnconfinedTestDispatcher()) {
        val childScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        subject.asStateFlow(childScope)

        // At least the one subscriber we just created should be registered.
        assertTrue(subject.count > 0)

        childScope.cancel()

        // After scope cancellation, the subscription should have been disposed.
        assertEquals(0, subject.count)
    }

    @Test
    fun asStateFlow_Does_Not_Update_After_Scope_Cancelled() = runTest(UnconfinedTestDispatcher()) {
        val childScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
        val stateFlow: StateFlow<String> = subject.asStateFlow(childScope)

        subject.onNext("before-cancel")
        assertEquals("before-cancel", stateFlow.value)

        childScope.cancel()

        subject.onNext("after-cancel")

        // Value should not have changed after the scope was canceled.
        assertEquals("before-cancel", stateFlow.value)
    }

    @Test
    fun asStateFlow_Multiple_Calls_Produce_Independent_StateFlows() = runTest(UnconfinedTestDispatcher()) {
        val stateFlow1: StateFlow<String> = subject.asStateFlow(this)
        val stateFlow2: StateFlow<String> = subject.asStateFlow(this)

        subject.onNext("updated")

        assertEquals("updated", stateFlow1.value)
        assertEquals("updated", stateFlow2.value)
    }

    // endregion
}
