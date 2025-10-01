package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.TealiumDispatchType
import com.tealium.tests.common.TestDispatcher
import io.mockk.coVerify
import io.mockk.verify
import org.junit.Test
import java.util.concurrent.TimeUnit

class DispatchManagerTrackTests : DispatchManagerTestsBase() {

    private val dispatch3: Dispatch =
        Dispatch.create("test3", TealiumDispatchType.Event, DataObject.EMPTY_OBJECT)

    @Test
    fun dispatchManager_SlowDispatcher_DoesNotDelayOthers() {
        val slowDispatcher = TestDispatcher.mock(dispatcher2Name) { dispatches, callback ->
            scheduler.schedule(TimeFrame(500, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
            }
        }

        scheduler.execute {
            modules.onNext(listOf(dispatcher1, slowDispatcher))

            queue[dispatcher1.id] = mutableSetOf(
                dispatch1,
                dispatch2
            )
            queue[slowDispatcher.id] = mutableSetOf(
                dispatch1,
                dispatch2
            )

            dispatchManager.startDispatchLoop()

            dispatchManager.track(dispatch3)
        }

        coVerify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch2), any())
            dispatcher1.dispatch(listOf(dispatch3), any())

            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch3), dispatcher1Name)
        }
        verify(timeout = 5000) {
            slowDispatcher.dispatch(listOf(dispatch1), any())
            slowDispatcher.dispatch(listOf(dispatch2), any())
            slowDispatcher.dispatch(listOf(dispatch3), any())

            queueManager.deleteDispatches(listOf(dispatch1), dispatcher2Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher2Name)
            queueManager.deleteDispatches(listOf(dispatch3), dispatcher2Name)
        }
    }

    @Test
    fun dispatchManager_SendsMultipleDispatchesInFlight_WhenDelayed() {
        dispatcher1 = TestDispatcher.mock(dispatcher1Name) { dispatches, callback ->
            scheduler.schedule(TimeFrame(500, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
            }
        }

        scheduler.execute {
            modules.onNext(listOf(dispatcher1))
            queue[dispatcher1.id] = mutableSetOf(
                dispatch1,
                dispatch2
            )

            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch3)
        }

        coVerify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher1.dispatch(listOf(dispatch2), any())
            dispatcher1.dispatch(listOf(dispatch3), any())

            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch3), dispatcher1Name)
        }
    }
}