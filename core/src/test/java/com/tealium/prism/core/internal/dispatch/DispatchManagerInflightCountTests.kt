package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.pubsub.Subscription
import com.tealium.tests.common.TestDispatcher
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
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
        dispatchers.onNext(listOf(dispatcher1))
        queue[dispatcher1.id] = mutableSetOf(dispatch1, dispatch2)

        dispatchManager = createDispatchManager(maxInFlight = 1)
        dispatchManager.startDispatchLoop()
        manualScheduler.runAll() // the follow-up dequeue of the full batch

        verify {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }
        verify(exactly = 0) {
            dispatcher1.dispatch(listOf(dispatch2), any())
        }
        assertEquals(1, inFlightEvents.value[dispatcher1.id]?.size)
        assertEquals(1, pendingCount(dispatcher1.id))
    }

    @Test
    fun dispatchManager_Stops_Dequeuing_At_The_Maximum_InFlight_When_Drained() {
        dispatcher1 = spyk(TestDispatcher(dispatcher1Name) { _, _ ->
            Subscription() // never completes
        })
        dispatchers.onNext(listOf(dispatcher1))
        queueManager.storeDispatches(testDispatches(MAXIMUM + 5), setOf(dispatcher1.id))

        dispatchManager = createDispatchManager()
        dispatchManager.startDispatchLoop()
        manualScheduler.runAll()

        assertEquals(MAXIMUM, inFlightEvents.value[dispatcher1.id]?.size)
        assertEquals(5, pendingCount(dispatcher1.id))
        // One dequeue per dispatch: none is attempted once the maximum is in flight.
        verify(exactly = MAXIMUM) { queueManager.dequeueDispatches(any(), dispatcher1Name) }
    }

    private fun pendingCount(dispatcherId: String): Int? {
        var count: Int? = null
        queueManager.queueSizePendingDispatch(dispatcherId).subscribeOnce { count = it }
        return count
    }

    private fun testDispatches(count: Int): List<Dispatch> =
        List(count) { testDispatch("event$it") }

    private companion object {
        const val MAXIMUM = DispatchManagerImpl.MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER
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

        verify(exactly = 0) {
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
        verify(exactly = 0) {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }

        inFlightEvents.onNext(mapOf(dispatcher1.id to setOf()))
        verify {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }
    }
}