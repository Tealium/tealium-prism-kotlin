package com.tealium.core.internal.dispatch

import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.persistence.TimeFrame
import com.tealium.tests.common.TestDispatcher
import io.mockk.verify
import io.mockk.spyk
import org.junit.Test
import java.util.concurrent.TimeUnit

class DispatchManagerInflightCountTests : DispatchManagerTestsBase() {

    override fun onAfterSetup() {
        dispatchManager = createDispatchManager(maxInFlight = 1)
    }

    @Test
    fun dispatchManager_StopsDispatchingEvents_WhenMaximumExceeded() {
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") {
            Observables.callback { observer ->
                scheduler.schedule(TimeFrame(2000, TimeUnit.MILLISECONDS)) {
                    observer.onNext(it)
                }
            }
        })
        scheduler.execute {
            dispatchers.onNext(setOf(dispatcher1))
            queue[dispatcher1.name] = mutableSetOf(dispatch1, dispatch2)

            dispatchManager = createDispatchManager(maxInFlight = 1)
            dispatchManager.startDispatchLoop()

            verify(timeout = 1000) {
                dispatcher1.dispatch(listOf(dispatch1))
            }
        }
        // delay in dispatcher emission will mean dispatch1 is in-flight for 2seconds
        verify(timeout = 1000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch2))
        }
    }

    @Test
    fun dispatchManager_DoesNotSendNewEvents_WhenMaximumExceeded() {
        scheduler.execute {
            onInFlightEvents.onNext(mapOf(dispatcher1.name to setOf("event1", "event2")))

            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch1)
        }
        verify(timeout = 1000, inverse = true) {
            queueManager.getQueuedEvents(dispatcher1, 1)
            dispatcher1.dispatch(listOf(dispatch1))
            dispatcher1.dispatch(listOf(dispatch2))
        }
    }

    @Test
    fun dispatchManager_StartsSendingEventsAgain_WhenInflightReturnsBelowMaximum() {
        scheduler.execute {
            onInFlightEvents.onNext(mapOf(dispatcher1.name to setOf("event1", "event2")))

            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch1)
        }
        verify(timeout = 1000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1))
        }

        scheduler.execute {
            onInFlightEvents.onNext(mapOf(dispatcher1.name to setOf()))
        }
        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1))
        }
    }
}