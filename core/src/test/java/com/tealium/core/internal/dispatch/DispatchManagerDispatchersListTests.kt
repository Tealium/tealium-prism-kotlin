package com.tealium.core.internal.dispatch

import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.persistence.TimeFrame
import com.tealium.tests.common.TestDispatcher
import io.mockk.coVerify
import io.mockk.spyk
import org.junit.Test
import java.util.concurrent.TimeUnit

class DispatchManagerDispatchersListTests : DispatchManagerTestsBase() {

    @Test
    fun dispatchManager_SendsDispatches_ToAllDispatchers() {
        val dispatcher2 = spyk(TestDispatcher("dispatcher_2"))
        dispatchers.onNext(setOf(dispatcher1, dispatcher2))

        dispatchManager.startDispatchLoop()

        dispatchManager.track(dispatch1)

        coVerify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1))
            dispatcher2.dispatch(listOf(dispatch1))
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher2Name)
        }
    }

    @Test
    fun dispatchManager_ContinuesSendingDispatches_ToEnabledDispatchers_WhenOneGetsDisabled() {
            val dispatcher2 = spyk(TestDispatcher("dispatcher_2"))
            dispatcher1 = spyk(TestDispatcher("dispatcher_1") {
                Observables.callback { observer ->
                    dispatchers.onNext(setOf(dispatcher2))
                    dispatchManager.track(dispatch2)

                    scheduler.schedule(TimeFrame(100, TimeUnit.MILLISECONDS)) {
                        observer.onNext(it)
                    }
                }
            })
            dispatchers.onNext(setOf(dispatcher1, dispatcher2))

            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch1) // dispatcher1 removed after first dispatch

            coVerify(timeout = 5000) {
                dispatcher1.dispatch(listOf(dispatch1))
                dispatcher2.dispatch(listOf(dispatch1))
                dispatcher2.dispatch(listOf(dispatch2))
                queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
                queueManager.deleteDispatches(listOf(dispatch1), dispatcher2Name)
                queueManager.deleteDispatches(listOf(dispatch2), dispatcher2Name)
            }
            coVerify(timeout = 5000, inverse = true) {
                dispatcher1.dispatch(listOf(dispatch2))
                queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
            }
        }

    @Test
    fun dispatchManager_StopsSendingDispatchesToDispatcher_WhenDispatcherGetsDisabled() {
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") {
            Observables.callback { observer ->
                dispatchers.onNext(setOf())
                dispatchManager.track(dispatch2)

                scheduler.schedule(TimeFrame(100, TimeUnit.MILLISECONDS)) {
                    observer.onNext(it)
                }
            }
        })
        dispatchers.onNext(setOf(dispatcher1))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1) // dispatcher1 removed after first dispatch

        coVerify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1))
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
        coVerify(timeout = 5000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch2))
            queueManager.deleteDispatches(listOf(dispatch2), dispatcher1Name)
        }
    }

    @Test
    fun dispatchManager_DoesNotCancelInflight_WhenDispatcherGetsDisabled() {
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") {
            Observables.callback { observer ->
                dispatchers.onNext(setOf())

                scheduler.schedule(TimeFrame(2000, TimeUnit.MILLISECONDS)) {
                    observer.onNext(it)
                }
            }
        })
        dispatchers.onNext(setOf(dispatcher1))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        coVerify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1))
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
    }
}