package com.tealium.core.internal.dispatch

import com.tealium.core.api.misc.TimeFrame
import com.tealium.tests.common.TestDispatcher
import io.mockk.verify
import org.junit.Test
import java.util.concurrent.TimeUnit

class DispatchManagerShutdownTests : DispatchManagerTestsBase() {

    @Test
    fun dispatchManager_StopsDispatching_WhenLoopCancelled() {
        dispatcher1 = TestDispatcher.mock("dispatcher1") { dispatches, callback ->
            scheduler.schedule(TimeFrame(500, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
            }
        }
        dispatchers.onNext(setOf(dispatcher1))
        queue[dispatcher1.id] = mutableSetOf(
            dispatch1
        )

        dispatchManager = createDispatchManager(maxInFlight = 1)

        scheduler.execute {
            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch2)
            dispatchManager.stopDispatchLoop()
        }

        verify(inverse = true, timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch2), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_CancelsDispatches_WhenScopeCancelled() {
        dispatcher1 = TestDispatcher.mock("dispatcher1") { dispatches, callback ->
            dispatchManager.stopDispatchLoop()

            scheduler.schedule(TimeFrame(500, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
            }
        }
        dispatchers.onNext(setOf(dispatcher1))
        queue[dispatcher1.id] = mutableSetOf(
            dispatch1
        )

        dispatchManager = createDispatchManager(maxInFlight = 1)

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch2)

        // TODO - maybe a better test than this.
//        scheduler.shutdownNow()

        verify(inverse = true, timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch2), any())

            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
        }
    }
}