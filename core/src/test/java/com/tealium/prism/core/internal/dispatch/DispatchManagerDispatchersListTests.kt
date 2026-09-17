package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.internal.pubsub.CompletedDisposable
import com.tealium.prism.core.internal.pubsub.Subscription
import com.tealium.tests.common.TestDispatcher
import com.tealium.tests.common.TestModule
import io.mockk.spyk
import io.mockk.verify
import org.junit.Test
import java.util.concurrent.TimeUnit

class DispatchManagerDispatchersListTests : DispatchManagerTestsBase() {

    @Test
    fun dispatchManager_Sends_Dispatches_To_All_Dispatchers() {
        val dispatcher2 = spyk(TestDispatcher("dispatcher_2"))
        modules.onNext(listOf(dispatcher1, dispatcher2))

        dispatchManager.startDispatchLoop()

        dispatchManager.track(dispatch1)

        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher2.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher2Name)
        }
    }

    @Test
    fun dispatchManager_Continues_Sending_Dispatches_To_Enabled_Dispatchers_When_One_Gets_Disabled() {
        val dispatcher2 = spyk(TestDispatcher("dispatcher_2"))
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") { dispatches, callback ->
            modules.onNext(listOf(dispatcher2))
            dispatchManager.track(dispatch2)

            scheduler.schedule(TimeFrame(100, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
            }
        })
        modules.onNext(listOf(dispatcher1, dispatcher2))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1) // dispatcher1 removed after first dispatch

        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            dispatcher2.dispatch(listOf(dispatch1), any())
            dispatcher2.dispatch(listOf(dispatch2), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher2Name)
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher2Name)
        }
        verify(exactly = 0) {
            dispatcher1.dispatch(listOf(dispatch2), any())
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
        }
    }


    @Test
    fun dispatchManager_Stops_Sending_Dispatches_To_Dispatcher_When_Dispatcher_Gets_Disabled() {
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") { dispatches, callback ->
            modules.onNext(listOf())
            dispatchManager.track(dispatch2)

            scheduler.schedule(TimeFrame(100, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
            }
        })
        modules.onNext(listOf(dispatcher1))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1) // dispatcher1 removed after first dispatch

        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
        verify(exactly = 0) {
            dispatcher1.dispatch(listOf(dispatch2), any())
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_Stops_Dequeueing_For_Removed_Dispatcher_When_Modules_List_Changes() {
        barrierFlow.onNext(BarrierState.Closed)
        modules.onNext(listOf(dispatcher1, dispatcher2))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1) // queued for both dispatchers; barrier keeps it there

        modules.onNext(listOf(dispatcher2)) // dispatcher1 disabled while dispatch1 is still queued
        barrierFlow.onNext(BarrierState.Open)

        verify(timeout = 5000) {
            dispatcher2.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher2Name)
        }
        verify(exactly = 0) {
            dispatcher1.dispatch(any(), any())
            queueManager.dequeueDispatches(any(), dispatcher1Name)
        }
    }

    /**
     * After a module list change there must be exactly one live dequeue loop per surviving
     * [com.tealium.prism.core.api.modules.Dispatcher]. The old chain never disposed a dispatcher's
     * loop, so a change stacked a second loop onto each survivor, and every extra loop is an extra
     * dequeue path draining the queue faster than intended.
     *
     * The barrier is held Closed so nothing dequeues until it is opened; each live loop then attempts
     * exactly one dequeue on opening, so a second attempt means a duplicated loop survived.
     */
    @Test
    fun dispatchManager_Keeps_One_Dequeue_Loop_Per_Survivor_When_Modules_List_Changes() {
        dispatcher1 = spyk(
            // dispatchLimit > 1 so a single queued dispatch doesn't spin the resubscribing loop, and
            // a handler that never completes so the dispatch stays in-flight during the measurement.
            TestDispatcher(dispatcher1Name, dispatchLimit = 2) { _, _ -> Subscription() }
        )
        barrierFlow.onNext(BarrierState.Closed)
        modules.onNext(listOf(dispatcher1, dispatcher2))

        dispatchManager.startDispatchLoop()

        modules.onNext(listOf(dispatcher1)) // dispatcher2 disabled; dispatcher1 survives
        queueManager.storeDispatches(listOf(dispatch1), setOf(dispatcher1Name))

        barrierFlow.onNext(BarrierState.Open) // every live loop attempts one dequeue

        verify(exactly = 1) {
            queueManager.dequeueDispatches(any(), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_Does_Not_Cancel_Inflight_When_Another_Dispatcher_Gets_Disabled() {
        dispatcher1 = spyk(TestDispatcher(dispatcher1Name) { dispatches, callback ->
            modules.onNext(modules.value - dispatcher2) // remove a *different* dispatcher

            scheduler.schedule(TimeFrame(100, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
            }
        })
        modules.onNext(listOf(dispatcher1, dispatcher2))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_Resumes_Dispatching_When_Dispatcher_Gets_ReEnabled() {
        modules.onNext(listOf(dispatcher2)) // dispatcher1 disabled before the loop starts

        dispatchManager.startDispatchLoop()

        modules.onNext(listOf(dispatcher1, dispatcher2)) // dispatcher1 re-enabled
        dispatchManager.track(dispatch1)

        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
    }

    /**
     * A module list emission that leaves the dispatcher list untouched (e.g. an unrelated module was
     * added, or settings were re-published) must not rebuild the per-dispatcher chains. The
     * `distinct` on the dispatcher list is what collapses it; a rebuild would resubscribe the
     * barriers and restart every dequeue loop.
     */
    @Test
    fun dispatchManager_Does_Not_Rebuild_Dispatcher_Chains_When_Dispatchers_Are_Unchanged() {
        modules.onNext(listOf(dispatcher1, dispatcher2))

        dispatchManager.startDispatchLoop()

        verify(exactly = 1) {
            barrierCoordinator.onBarriersState(dispatcher1Name)
        }

        // Same dispatcher instances, same order; only an unrelated module was added.
        modules.onNext(listOf(dispatcher1, dispatcher2, TestModule("data_module")))

        verify(exactly = 1) {
            barrierCoordinator.onBarriersState(dispatcher1Name)
        }
    }

    /**
     * A second [DispatchManagerImpl.startDispatchLoop] while a loop is already running must be a
     * no-op. Without the guard the second call would subscribe a whole new chain alongside the
     * first, giving every dispatcher a second barrier subscription and a second dequeue loop.
     */
    @Test
    fun startDispatchLoop_Does_Not_Resubscribe_When_Already_Started() {
        modules.onNext(listOf(dispatcher1, dispatcher2))

        dispatchManager.startDispatchLoop()
        dispatchManager.startDispatchLoop()

        verify(exactly = 1) {
            barrierCoordinator.onBarriersState(dispatcher1Name)
        }
        verify(exactly = 1) {
            barrierCoordinator.onBarriersState(dispatcher2Name)
        }
    }

    /**
     * The "already started" guard must also hold when the second call is re-entered from within the
     * synchronous subscribe, such as from a [com.tealium.prism.core.api.modules.Dispatcher.dispatch]
     * triggered by the initial dequeue. The loop handle is assigned before subscribing precisely so
     * that this re-entrant call sees a running loop and does not stack a second chain.
     */
    @Test
    fun startDispatchLoop_Ignores_Reentrant_Call_When_Invoked_From_Dispatcher_During_Subscribe() {
        dispatcher1 = spyk(TestDispatcher(dispatcher1Name) { dispatches, callback ->
            dispatchManager.startDispatchLoop() // re-entered while the first subscribe is still running
            callback.onComplete(dispatches)
            CompletedDisposable
        })
        modules.onNext(listOf(dispatcher1))
        queue[dispatcher1Name] = mutableSetOf(dispatch1) // seeded so the initial subscribe dispatches synchronously

        dispatchManager.startDispatchLoop()

        verify(exactly = 1) {
            barrierCoordinator.onBarriersState(dispatcher1Name)
        }
        verify(exactly = 1) {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }
        verify {
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
    }

    /**
     * A dispatcher replaced by a *new* instance with the same id must have its chain rebuilt, so that
     * dispatches reach the new instance. This is why the dispatcher list is compared by instance
     * identity rather than by [com.tealium.prism.core.api.modules.Dispatcher.id].
     */
    @Test
    fun dispatchManager_Rebuilds_Dispatcher_Chain_When_Dispatcher_Instance_Is_Replaced() {
        modules.onNext(listOf(dispatcher1))

        dispatchManager.startDispatchLoop()

        val replacement = spyk(TestDispatcher(dispatcher1Name)) // same id, different instance
        modules.onNext(listOf(replacement))

        dispatchManager.track(dispatch1)

        verify(timeout = 5000) {
            replacement.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
        verify(exactly = 0) {
            dispatcher1.dispatch(any(), any())
        }
    }

    @Test
    fun dispatchManager_Does_Not_Cancel_Inflight_When_Dispatcher_Gets_Disabled() {
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") { dispatches, callback ->
            modules.onNext(listOf())

            scheduler.schedule(TimeFrame(2000, TimeUnit.MILLISECONDS)) {
                callback.onComplete(dispatches)
            }
        })
        modules.onNext(listOf(dispatcher1))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
    }
}