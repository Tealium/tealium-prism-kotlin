package com.tealium.core.internal.dispatch

import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.misc.TimeFrame
import com.tealium.tests.common.TestDispatcher
import io.mockk.verify
import io.mockk.every
import io.mockk.spyk
import org.junit.Test
import java.util.concurrent.TimeUnit

class DispatchManagerBarrierTests : DispatchManagerTestsBase() {

    @Test
    fun dispatchManager_SendsDispatchesToDispatcher_WhenBarriersAreOpen() {
        dispatchManager.startDispatchLoop()

        dispatchManager.track(dispatch1)

        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), any())
        }
    }

    @Test
    fun dispatchManager_DoesNotSendDispatchesToDispatcher_WhenBarriersAreClosed() {
        barrierFlow.onNext(BarrierState.Closed)

        dispatchManager.startDispatchLoop()

        dispatchManager.track(dispatch1)

        verify(timeout = 5000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), any())
        }
    }

    @Test
    fun dispatchManager_SendQueuedDispatchesToDispatcher_WhenBarriersAreOpened() {
        barrierFlow.onNext(BarrierState.Closed)
        dispatchManager.startDispatchLoop()

        queue[dispatcher1.name] = mutableSetOf(dispatch1, dispatch2)

        barrierFlow.onNext(BarrierState.Open)

        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch2), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_StopsSendingDispatchesToDispatcher_WhenBarriersGetClosed() {
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") { dispatches, callback ->
            barrierFlow.onNext(BarrierState.Closed)
            dispatchManager.track(dispatch2)

            scheduler.schedule(TimeFrame(100, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
            }
        })
        dispatchers.onNext(setOf(dispatcher1))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1) // closes after first dispatch

        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
        verify(timeout = 5000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch2), any())
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_DoesNotCancelInflight_WhenBarriersGetClosed() {
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") { dispatches, callback ->
            barrierFlow.onNext(BarrierState.Closed)

            scheduler.schedule(TimeFrame(2000, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
            }
        })
        dispatchers.onNext(setOf(dispatcher1))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_ClosedBarriers_DoNotInterfere_WithOtherDispatchers() {
        val dispatcher2 = spyk(TestDispatcher("dispatcher_2"))
        every { barrierCoordinator.onBarriersState("dispatcher_2") } returns Observables.stateSubject(
            BarrierState.Closed
        )
        dispatchers.onNext(setOf(dispatcher1, dispatcher2))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
        verify(timeout = 5000, inverse = true) {
            dispatcher2.dispatch(listOf(dispatch2), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher2Name)
        }
    }
}