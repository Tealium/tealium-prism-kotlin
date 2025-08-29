package com.tealium.core.internal.dispatch

import com.tealium.core.internal.pubsub.Subscription
import com.tealium.tests.common.TestDispatcher
import io.mockk.spyk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DispatchManagerInflightCountTests : DispatchManagerTestsBase() {

    override fun onAfterSetup() {
        dispatchManager = createDispatchManager(maxInFlight = 1)
    }

    @Test
    fun dispatchManager_StopsDispatchingEvents_WhenMaximumExceeded() {
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") { _, _ ->
            // never completes; should stay at 1 in-flight
            Subscription()
        })
        modules.onNext(listOf(dispatcher1))
        queue[dispatcher1.id] = mutableSetOf(dispatch1, dispatch2)

        dispatchManager = createDispatchManager(maxInFlight = 1)
        dispatchManager.startDispatchLoop()

        verify {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }
        verify(inverse = true) {
            dispatcher1.dispatch(listOf(dispatch2), any())
        }
    }

    @Test
    fun dispatchManager_DoesNotSendNewEvents_WhenMaximumExceeded() {
        inFlightEvents.onNext(
            mapOf(
                dispatcher1.id to setOf(
                    testDispatch("event1"),
                    testDispatch("event2")
                )
            )
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(inverse = true) {
            queueManager.dequeueDispatches(1, dispatcher1Name)
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch2), any())
        }
    }

    @Test
    fun dispatchManager_StartsSendingEventsAgain_WhenInflightReturnsBelowMaximum() {
        inFlightEvents.onNext(
            mapOf(
                dispatcher1.id to setOf(
                    testDispatch("event1"),
                    testDispatch("event2")
                )
            )
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)
        verify(inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }

        inFlightEvents.onNext(mapOf(dispatcher1.id to setOf()))
        verify {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }
    }
}