package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.TealiumDispatchType
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.tests.common.TestDispatcher
import io.mockk.Ordering
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class DispatchManagerQueueTests : DispatchManagerTestsBase() {

    private val dispatch3: Dispatch =
        Dispatch.create("test3", TealiumDispatchType.Event, DataObject.EMPTY_OBJECT)
    private val dispatch4: Dispatch =
        Dispatch.create("test4", TealiumDispatchType.Event, DataObject.EMPTY_OBJECT)
    private val dispatch5: Dispatch =
        Dispatch.create("test5", TealiumDispatchType.Event, DataObject.EMPTY_OBJECT)

    @Test
    fun dispatchManager_SendsIndividualDispatches_ToAllDispatcher() {
        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher2.dispatch(listOf(dispatch1), any())

            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher2Name)
        }
    }

    @Test
    fun dispatchManager_SendsBatchesOfDispatchesToDispatcher_WhenQueued() {
        dispatcher1 = TestDispatcher.mock(dispatcher1Name, dispatchLimit = 2)
        modules.onNext(listOf(dispatcher1))
        queue[dispatcher1.id] = mutableSetOf(
            dispatch1,
            dispatch2,
            dispatch3,
            dispatch4,
            dispatch5,
        )

        scheduler.execute {
            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch1)
        }

        verify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1, dispatch2), any())
            dispatcher1.dispatch(listOf(dispatch3, dispatch4), any())
            dispatcher1.dispatch(listOf(dispatch5), any())

            queueManager.deleteDispatches(listOf(dispatch1, dispatch2), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch3, dispatch4), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch5), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_SendsBatchesOfDispatchesToDispatcher_WhenMultipleSent() {
        dispatcher1 = TestDispatcher.mock(dispatcher1Name, dispatchLimit = 2)
        modules.onNext(listOf(dispatcher1))
        queue[dispatcher1.id] = mutableSetOf(
            dispatch1,
            dispatch2,
            dispatch3,
            dispatch4,
        )
        scheduler.execute {
            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch5)
        }
        verify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1, dispatch2), any())
            dispatcher1.dispatch(listOf(dispatch3, dispatch4), any())
            dispatcher1.dispatch(listOf(dispatch5), any())

            queueManager.deleteDispatches(listOf(dispatch1, dispatch2), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch3, dispatch4), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch5), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_DispatchesEvent_WhenQueueWasEmpty() {
        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_SendsDispatchesToDispatcher_InOrder() {
        queue[dispatcher1.id] = mutableSetOf(
            dispatch1,
            dispatch2,
            dispatch3
        )

        scheduler.execute {
            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch4)
            dispatchManager.track(dispatch5)
        }

        verify(timeout = 1000, ordering = Ordering.ORDERED) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch2), any())
            dispatcher1.dispatch(listOf(dispatch3), any())
            dispatcher1.dispatch(listOf(dispatch4), any())
            dispatcher1.dispatch(listOf(dispatch5), any())
        }
    }

    @Test
    fun dispatchManager_SendsBatchesToDispatcher_InOrder() {
        dispatcher1 = TestDispatcher.mock(dispatcher1Name, dispatchLimit = 2)
        modules.onNext(listOf(dispatcher1))
        queue[dispatcher1.id] = mutableSetOf(
            dispatch1,
            dispatch2,
            dispatch3,
            dispatch4
        )

        scheduler.execute {
            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch5)
        }

        verify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1, dispatch2), any())
            dispatcher1.dispatch(listOf(dispatch3, dispatch4), any())
            dispatcher1.dispatch(listOf(dispatch5), any())

            queueManager.deleteDispatches(listOf(dispatch1, dispatch2), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch3, dispatch4), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch5), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_SendsQueuedEvents_ToCorrectDispatcher() {
        queue[dispatcher1.id] = mutableSetOf(
            dispatch1,
        )
        queue[dispatcher2.id] = mutableSetOf(
            dispatch2,
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch3) // both receive 3

        verify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch3), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch3), dispatcher1Name)

            dispatcher2.dispatch(listOf(dispatch2), any())
            dispatcher2.dispatch(listOf(dispatch3), any())
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher2Name)
            queueManager.deleteDispatches(listOf(dispatch3), dispatcher2Name)
        }
        verify(timeout = 1000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch2), any())
            dispatcher2.dispatch(listOf(dispatch1), any())
        }
    }

    @Test
    fun track_Notifies_DispatchAccepted_WhenDispatch_IsQueued() {
        val onComplete: (TrackResult) -> Unit = mockk(relaxed = true)

        dispatchManager.track(dispatch1, onComplete)

        verify {
            onComplete(match {
                it.status == TrackResult.Status.Accepted
            })
        }
    }
}