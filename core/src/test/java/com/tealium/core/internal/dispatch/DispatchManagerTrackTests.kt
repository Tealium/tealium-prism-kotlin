package com.tealium.core.internal.dispatch

import com.tealium.core.api.Dispatch
import com.tealium.core.api.TealiumDispatchType
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.persistence.TimeFrame
import com.tealium.tests.common.TestDispatcher
import io.mockk.coVerify
import io.mockk.verify
import org.junit.Test
import java.util.concurrent.TimeUnit

class DispatchManagerTrackTests : DispatchManagerTestsBase() {

    private val dispatch3: Dispatch =
        Dispatch.create("test3", TealiumDispatchType.Event, TealiumBundle.EMPTY_BUNDLE)

    @Test
    fun dispatchManager_SlowDispatcher_DoesNotDelayOthers() {
        val slowDispatcher = TestDispatcher.mock("dispatcher2") { dispatches ->
            Observables.callback { observer ->
                scheduler.schedule(TimeFrame(500, TimeUnit.MILLISECONDS)) {
                    observer.onNext(dispatches)
                }
            }
        }

        scheduler.execute {
            dispatchers.onNext(setOf(dispatcher1, slowDispatcher))
            queue[dispatcher1.name] = mutableSetOf(
                dispatch1,
                dispatch2
            )
            queue[slowDispatcher.name] = mutableSetOf(
                dispatch1,
                dispatch2
            )

            dispatchManager.startDispatchLoop()

            dispatchManager.track(dispatch3)
        }

        coVerify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1))
            dispatcher1.dispatch(listOf(dispatch2))
            dispatcher1.dispatch(listOf(dispatch3))

            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1)
            queueManager.deleteDispatches(listOf(dispatch3), dispatcher1)
        }
        verify(timeout = 5000) {
            slowDispatcher.dispatch(listOf(dispatch1))
            slowDispatcher.dispatch(listOf(dispatch2))
            slowDispatcher.dispatch(listOf(dispatch3))

            queueManager.deleteDispatches(listOf(dispatch1), slowDispatcher)
            queueManager.deleteDispatches(listOf(dispatch2), slowDispatcher)
            queueManager.deleteDispatches(listOf(dispatch3), slowDispatcher)
        }
    }

    @Test
    fun dispatchManager_SendsMultipleDispatchesInFlight_WhenDelayed() {
        dispatcher1 = TestDispatcher.mock("dispatcher1") { dispatches ->
            Observables.callback { observer ->
                scheduler.schedule(TimeFrame(500, TimeUnit.MILLISECONDS)) {
                    observer.onNext(dispatches)
                }
            }
        }

        scheduler.execute {
            dispatchers.onNext(setOf(dispatcher1))
            queue[dispatcher1.name] = mutableSetOf(
                dispatch1,
                dispatch2
            )

            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch3)
        }

        coVerify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1))
            dispatcher1.dispatch(listOf(dispatch2))
            dispatcher1.dispatch(listOf(dispatch3))

            queueManager.deleteDispatches(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch2), any())
            queueManager.deleteDispatches(listOf(dispatch3), any())
        }
    }
}