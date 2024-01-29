package com.tealium.core.internal.dispatch

import com.tealium.tests.common.TestDispatcher
import io.mockk.coVerify
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DispatchManagerShutdownTests : DispatchManagerTestsBase() {

    @Test
    fun dispatchManager_StopsDispatching_WhenLoopCancelled() = runTest {
        dispatcher1 = TestDispatcher.mock("dispatcher1") { dispatches ->
            flow {
                dispatchManager.stopDispatchLoop()
                delay(500)
                emit(dispatches)
            }
        }
        dispatchers.emit(setOf(dispatcher1))
        queue[dispatcher1.name] = mutableSetOf(
            dispatch1
        )

        dispatchManager = createDispatchManager(maxInFlight = 1)

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch2)

        coVerify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1))

            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
        }
        coVerify(inverse = true, timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch2))

            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1)
        }
    }

    @Test
    fun dispatchManager_CancelsDispatches_WhenScopeCancelled() = runTest {
        dispatcher1 = TestDispatcher.mock("dispatcher1") { dispatches ->
            flow {
                tealiumScope.cancel()
                delay(500)
                emit(dispatches)
            }
        }
        dispatchers.emit(setOf(dispatcher1))
        queue[dispatcher1.name] = mutableSetOf(
            dispatch1
        )

        dispatchManager = createDispatchManager(maxInFlight = 1)

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch2)

        coVerify(inverse = true, timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1))
            dispatcher1.dispatch(listOf(dispatch2))

            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1)
        }
    }
}