package com.tealium.core.internal.dispatch

import com.tealium.core.api.Dispatch
import com.tealium.core.api.TealiumDispatchType
import com.tealium.core.api.data.TealiumBundle
import com.tealium.tests.common.TestDispatcher
import io.mockk.Ordering
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DispatchManagerQueueTests : DispatchManagerTestsBase() {

    private val dispatch3: Dispatch =
        Dispatch.create("test3", TealiumDispatchType.Event, TealiumBundle.EMPTY_BUNDLE)
    private val dispatch4: Dispatch =
        Dispatch.create("test4", TealiumDispatchType.Event, TealiumBundle.EMPTY_BUNDLE)
    private val dispatch5: Dispatch =
        Dispatch.create("test5", TealiumDispatchType.Event, TealiumBundle.EMPTY_BUNDLE)

    @Test
    fun dispatchManager_SendsIndividualDispatches_ToAllDispatcher() = runTest {
        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        coVerify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1))
            dispatcher2.dispatch(listOf(dispatch1))

            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher2)
        }
    }

    @Test
    fun dispatchManager_SendsBatchesOfDispatchesToDispatcher_WhenQueued() = runTest {
        dispatcher1 = TestDispatcher.mock("dispatcher1", dispatchLimit = 2)
        dispatchers.emit(setOf(dispatcher1))
        queue[dispatcher1.name] = mutableSetOf(
            dispatch1,
            dispatch2,
            dispatch3,
            dispatch4,
            dispatch5,
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        coVerify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1, dispatch2))
            dispatcher1.dispatch(listOf(dispatch3, dispatch4))
            dispatcher1.dispatch(listOf(dispatch5))

            queueManager.deleteDispatches(listOf(dispatch1, dispatch2), any())
            queueManager.deleteDispatches(listOf(dispatch3, dispatch4), any())
            queueManager.deleteDispatches(listOf(dispatch5), dispatcher1)
        }
    }

    @Test
    fun dispatchManager_SendsBatchesOfDispatchesToDispatcher_WhenMultipleSent() = runTest {
        dispatcher1 = TestDispatcher.mock("dispatcher1", dispatchLimit = 2)
        dispatchers.emit(setOf(dispatcher1))
        queue[dispatcher1.name] = mutableSetOf(
            dispatch1,
            dispatch2,
            dispatch3
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch4)
        dispatchManager.track(dispatch5)

        coVerify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1, dispatch2))
            dispatcher1.dispatch(listOf(dispatch3, dispatch4))
            dispatcher1.dispatch(listOf(dispatch5))

            queueManager.deleteDispatches(listOf(dispatch1, dispatch2), dispatcher1)
            queueManager.deleteDispatches(listOf(dispatch3, dispatch4), dispatcher1)
            queueManager.deleteDispatches(listOf(dispatch5), dispatcher1)
        }
    }

    @Test
    fun dispatchManager_DispatchesEvent_WhenQueueWasEmpty() = runTest {
        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        coVerify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1))
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
        }
    }

    @Test
    fun dispatchManager_SendsDispatchesToDispatcher_InOrder() = runTest {
        queue[dispatcher1.name] = mutableSetOf(
            dispatch1,
            dispatch2,
            dispatch3
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch4)
        dispatchManager.track(dispatch5)

        coVerify(timeout = 1000, ordering = Ordering.ORDERED) {
            dispatcher1.dispatch(listOf(dispatch1))
            dispatcher1.dispatch(listOf(dispatch2))
            dispatcher1.dispatch(listOf(dispatch3))
            dispatcher1.dispatch(listOf(dispatch4))
            dispatcher1.dispatch(listOf(dispatch5))
        }
    }

    @Test
    fun dispatchManager_SendsBatchesToDispatcher_InOrder() = runTest {
        dispatcher1 = TestDispatcher.mock("dispatcher1", dispatchLimit = 2)
        dispatchers.emit(setOf(dispatcher1))
        queue[dispatcher1.name] = mutableSetOf(
            dispatch1,
            dispatch2,
            dispatch3,
            dispatch4
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch5)

        coVerify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1, dispatch2))
            dispatcher1.dispatch(listOf(dispatch3, dispatch4))
            dispatcher1.dispatch(listOf(dispatch5))

            queueManager.deleteDispatches(listOf(dispatch1, dispatch2), any())
            queueManager.deleteDispatches(listOf(dispatch3, dispatch4), any())
            queueManager.deleteDispatches(listOf(dispatch5), any())
        }
    }

    @Test
    fun dispatchManager_SendsQueuedEvents_ToCorrectDispatcher() = runTest {
        queue[dispatcher1.name] = mutableSetOf(
            dispatch1,
        )
        queue[dispatcher2.name] = mutableSetOf(
            dispatch2,
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch3) // both receive 3

        coVerify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1))
            dispatcher1.dispatch(listOf(dispatch3))
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
            queueManager.deleteDispatches(listOf(dispatch3), dispatcher1)

            dispatcher2.dispatch(listOf(dispatch2))
            dispatcher2.dispatch(listOf(dispatch3))
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher2)
            queueManager.deleteDispatches(listOf(dispatch3), dispatcher2)
        }
        coVerify(timeout = 1000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch2))
            dispatcher2.dispatch(listOf(dispatch1))
        }
    }
}