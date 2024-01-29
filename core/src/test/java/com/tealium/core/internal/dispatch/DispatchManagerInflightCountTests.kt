package com.tealium.core.internal.dispatch

import com.tealium.tests.common.TestDispatcher
import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DispatchManagerInflightCountTests : DispatchManagerTestsBase() {

    override fun onAfterSetup() {
        dispatchManager = createDispatchManager(maxInFlight = 1)
    }

    @Test
    fun dispatchManager_StopsDispatchingEvents_WhenMaximumExceeded() = runTest {
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") {
            flow {
                delay(2000)
                emit(it)
            }
        })
        dispatchers.emit(setOf(dispatcher1))
        queue[dispatcher1.name] = mutableSetOf(dispatch1, dispatch2)

        dispatchManager.startDispatchLoop()

        coVerify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1))
        }
        // delay in dispatcher emission will mean dispatch1 is in-flight for 2seconds
        coVerify(timeout = 1000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch2))
        }
    }

    @Test
    fun dispatchManager_DoesNotSendNewEvents_WhenMaximumExceeded() = runTest {
        onInFlightEvents.emit(mapOf(dispatcher1.name to setOf("event1", "event2")))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        coVerify(timeout = 1000, inverse = true) {
            queueManager.getQueuedEvents(dispatcher1, 1)
            dispatcher1.dispatch(listOf(dispatch1))
            dispatcher1.dispatch(listOf(dispatch2))
        }
    }

    @Test
    fun dispatchManager_StartsSendingEventsAgain_WhenInflightReturnsBelowMaximum() = runTest {
        onInFlightEvents.emit(mapOf(dispatcher1.name to setOf("event1", "event2")))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        coVerify(timeout = 1000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1))
        }

        onInFlightEvents.emit(mapOf(dispatcher1.name to setOf()))

        coVerify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1))
        }
    }
}