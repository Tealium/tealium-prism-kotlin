package com.tealium.core.internal.dispatch

import com.tealium.core.api.BarrierState
import com.tealium.tests.common.TestDispatcher
import io.mockk.coVerify
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DispatchManagerBarrierTests: DispatchManagerTestsBase() {

    @Test
    fun dispatchManager_SendsDispatchesToDispatcher_WhenBarriersAreOpen() = runTest {
        dispatchManager.startDispatchLoop()

        dispatchManager.track(dispatch1)

        coVerify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1))
            queueManager.deleteDispatches(listOf(dispatch1), any())
        }
    }

    @Test
    fun dispatchManager_DoesNotSendDispatchesToDispatcher_WhenBarriersAreClosed() = runTest {
        barrierFlow.emit(BarrierState.Closed)

        dispatchManager.startDispatchLoop()

        dispatchManager.track(dispatch1)

        coVerify(timeout = 5000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1))
            queueManager.deleteDispatches(listOf(dispatch1), any())
        }
    }

    @Test
    fun dispatchManager_SendQueuedDispatchesToDispatcher_WhenBarriersAreOpened() = runTest {
        barrierFlow.emit(BarrierState.Closed)
        dispatchManager.startDispatchLoop()

        queue[dispatcher1.name] = mutableSetOf(dispatch1, dispatch2)

        barrierFlow.emit(BarrierState.Open)

        coVerify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1))
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
            dispatcher1.dispatch(listOf(dispatch2))
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1)
        }
    }

    @Test
    fun dispatchManager_StopsSendingDispatchesToDispatcher_WhenBarriersGetClosed() = runTest {
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") {
            flow {
                barrierFlow.emit(BarrierState.Closed)
                dispatchManager.track(dispatch2)
                delay(100)
                emit(it)
            }
        })
        dispatchers.emit(setOf(dispatcher1))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1) // closes after first dispatch

        coVerify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1))
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
        }
        coVerify(timeout = 5000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch2))
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1)
        }
    }

    @Test
    fun dispatchManager_DoesNotCancelInflight_WhenBarriersGetClosed() = runTest {
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") {
            flow {
                barrierFlow.emit(BarrierState.Closed)
                delay(2000)
                emit(it)
            }
        })
        dispatchers.emit(setOf(dispatcher1))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        coVerify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1))
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
        }
    }

    @Test
    fun dispatchManager_ClosedBarriers_DoNotInterfere_WithOtherDispatchers() = runTest {
        val dispatcher2 = spyk(TestDispatcher("dispatcher_2"))
        every { barrierCoordinator.onBarriersState("dispatcher_2") } returns MutableStateFlow(
            BarrierState.Closed
        )
        dispatchers.emit(setOf(dispatcher1, dispatcher2))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        coVerify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1))
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
        }
        coVerify(timeout = 5000, inverse = true) {
            dispatcher2.dispatch(listOf(dispatch2))
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher2)
        }
    }
}