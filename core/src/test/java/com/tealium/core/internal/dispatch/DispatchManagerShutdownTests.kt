package com.tealium.core.internal.dispatch

import com.tealium.core.internal.observables.Observables
import com.tealium.tests.common.TestDispatcher
import io.mockk.verify
import org.junit.Test
import java.util.concurrent.TimeUnit

class DispatchManagerShutdownTests : DispatchManagerTestsBase() {

    @Test
    fun dispatchManager_StopsDispatching_WhenLoopCancelled() {
        dispatcher1 = TestDispatcher.mock("dispatcher1") { dispatches ->
            Observables.callback { observer ->
                executorService.schedule({
                    observer.onNext(dispatches)
                }, 500, TimeUnit.MILLISECONDS)
            }
        }
        dispatchers.onNext(setOf(dispatcher1))
        queue[dispatcher1.name] = mutableSetOf(
            dispatch1
        )

        dispatchManager = createDispatchManager(maxInFlight = 1)

        executorService.execute {
            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch2)
            dispatchManager.stopDispatchLoop()
        }

        verify(inverse = true, timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1))
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
            dispatcher1.dispatch(listOf(dispatch2))
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1)
        }
    }

    @Test
    fun dispatchManager_CancelsDispatches_WhenScopeCancelled() {
        dispatcher1 = TestDispatcher.mock("dispatcher1") { dispatches ->
            Observables.callback { observer ->
                dispatchManager.stopDispatchLoop()

                executorService.schedule({
                    observer.onNext(dispatches)
                }, 500, TimeUnit.MILLISECONDS)
            }
        }
        dispatchers.onNext(setOf(dispatcher1))
        queue[dispatcher1.name] = mutableSetOf(
            dispatch1
        )

        dispatchManager = createDispatchManager(maxInFlight = 1)

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch2)

        // TODO - maybe a better test than this.
        executorService.shutdownNow()

        verify(inverse = true, timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1))
            dispatcher1.dispatch(listOf(dispatch2))

            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1)
        }
    }
}