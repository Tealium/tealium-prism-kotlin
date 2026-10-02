package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.internal.pubsub.CompletedDisposable
import com.tealium.prism.core.internal.pubsub.Subscription
import com.tealium.tests.common.TestDispatcher
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DispatchManagerShutdownTests : DispatchManagerTestsBase() {

    @Test
    fun dispatchManager_StopsDispatching_WhenLoopCancelled() {
        val completed = CountDownLatch(1)
        dispatcher1 = TestDispatcher.mock("dispatcher1") { dispatches, callback ->
            scheduler.schedule(TimeFrame(500, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
                completed.countDown()
            }
        }
        dispatchers.onNext(listOf(dispatcher1))
        queue[dispatcher1.id] = mutableSetOf(
            dispatch1
        )

        dispatchManager = createDispatchManager(maxInFlight = 1)

        scheduler.execute {
            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch2)
            dispatchManager.stopDispatchLoop()
        }

        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }
        // Stopping the loop disposes the in-flight dispatch, which cancels the scheduled
        // callback, so completion must never arrive.
        assertFalse(completed.await(1, TimeUnit.SECONDS))
        verify(exactly = 0) {
            dispatcher1.dispatch(listOf(dispatch2), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_CancelsDispatches_WhenScopeCancelled() {
        val completed = CountDownLatch(1)
        dispatcher1 = TestDispatcher.mock("dispatcher1") { dispatches, callback ->
            dispatchManager.stopDispatchLoop()

            scheduler.schedule(TimeFrame(500, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
                completed.countDown()
            }
        }
        dispatchers.onNext(listOf(dispatcher1))
        queue[dispatcher1.id] = mutableSetOf(
            dispatch1
        )

        dispatchManager = createDispatchManager(maxInFlight = 1)

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch2)

        // TODO - maybe a better test than this.
//        scheduler.shutdownNow()

        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }
        // The stop issued from inside dispatch() is honored even though the loop subscription is
        // still being set up, so the in-flight dispatch is disposed and its callback never fires.
        assertFalse(completed.await(1, TimeUnit.SECONDS))
        verify(exactly = 0) {
            dispatcher1.dispatch(listOf(dispatch2), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_DoesNotProcessDispatches_WhenDisposedBeforeDispatcherCallback() {
        var lateCallback: Callback<List<Dispatch>>? = null
        dispatcher1 = spyk(TestDispatcher(dispatcher1Name) { _, callback ->
            lateCallback = callback
            Subscription()
        })
        dispatchers.onNext(listOf(dispatcher1))
        queue[dispatcher1Name] = mutableSetOf(dispatch1)

        dispatchManager = createDispatchManager(maxInFlight = 1)
        dispatchManager.startDispatchLoop()

        verify(timeout = 1000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }

        dispatchManager.stopDispatchLoop()
        lateCallback?.onComplete(listOf(dispatch1))

        verify(exactly = 0) {
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_DoesNotProcessDispatches_WhenDisposedAfterLoadRulesDropSome() {
        val dispatch3 = Dispatch.create("test3", DispatchType.Event, DataObject.EMPTY_OBJECT)
        var lateCallback: Callback<List<Dispatch>>? = null

        dispatcher1 = spyk(TestDispatcher(dispatcher1Name, dispatchLimit = 2) { _, callback ->
            lateCallback = callback
            Subscription()
        })
        dispatchers.onNext(listOf(dispatcher1))

        every { loadRuleEngine.evaluateLoadRules(dispatcher1, any()) } answers {
            val all = arg<List<Dispatch>>(1)
            DispatchSplit(all.filter { it.id == dispatch3.id }, all.filter { it.id != dispatch3.id })
        }

        queue[dispatcher1Name] = mutableSetOf(dispatch1, dispatch3)

        dispatchManager = createDispatchManager(maxInFlight = 2)
        dispatchManager.startDispatchLoop()

        verify(timeout = 1000) {
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }

        dispatchManager.stopDispatchLoop()
        lateCallback?.onComplete(listOf(dispatch3))

        verify(exactly = 0) {
            queueManager.deleteDispatches(listOf(dispatch3), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_DeletesDispatchExactlyOnce_WhenDispatcherCallsBackTwiceWithSameId() {
        val dispatch3 = Dispatch.create("test3", DispatchType.Event, DataObject.EMPTY_OBJECT)
        var lateCallback: Callback<List<Dispatch>>? = null

        dispatcher1 = spyk(TestDispatcher(dispatcher1Name, dispatchLimit = 2) { dispatches, callback ->
            callback.onComplete(listOf(dispatches.first()))
            lateCallback = callback
            CompletedDisposable
        })
        dispatchers.onNext(listOf(dispatcher1))

        every { loadRuleEngine.evaluateLoadRules(dispatcher1, any()) } answers {
            val all = arg<List<Dispatch>>(1)
            DispatchSplit(listOf(all.first()), all.drop(1))
        }

        queue[dispatcher1Name] = mutableSetOf(dispatch1, dispatch3)

        dispatchManager = createDispatchManager(maxInFlight = 2)
        dispatchManager.startDispatchLoop()

        verify(timeout = 1000) {
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch3), dispatcher1Name)
        }

        lateCallback?.onComplete(listOf(dispatch1))

        verify(exactly = 1) {
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
    }

    /**
     * Stopping the loop must leave the manager restartable. [DispatchManagerImpl.stopDispatchLoop]
     * clears the loop handle, so the next [DispatchManagerImpl.startDispatchLoop] is not mistaken
     * for a duplicate call and makes a fresh subscription that drains what was queued while stopped.
     */
    @Test
    fun startDispatchLoop_Resumes_Dispatching_When_Called_After_Stop() {
        dispatchers.onNext(listOf(dispatcher1))

        dispatchManager.startDispatchLoop()
        dispatchManager.stopDispatchLoop()

        dispatchManager.track(dispatch1) // queued while the loop is stopped

        verify(exactly = 0) {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }

        dispatchManager.startDispatchLoop()

        verify {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
        // One barrier subscription per start proves the second start built a fresh chain.
        verify(exactly = 2) {
            barrierCoordinator.onBarriersState(dispatcher1Name)
        }
    }

    /**
     * A stop issued before any start must be a harmless no-op. If it threw, or left the manager in a
     * state where a later start was ignored, a premature shutdown call would poison the manager.
     */
    @Test
    fun stopDispatchLoop_Does_Nothing_When_Loop_Was_Never_Started() {
        dispatchers.onNext(listOf(dispatcher1))
        queue[dispatcher1Name] = mutableSetOf(dispatch1)

        dispatchManager.stopDispatchLoop() // never started; must not throw

        dispatchManager.startDispatchLoop()

        verify {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
    }
}
