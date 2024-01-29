package com.tealium.core.internal.dispatch

import com.tealium.tests.common.TestDispatcher
import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DispatchManagerDispatchersListTests : DispatchManagerTestsBase() {

    @Test
    fun dispatchManager_SendsDispatches_ToAllDispatchers() = runTest {
        val dispatcher2 = spyk(TestDispatcher("dispatcher_2"))
        dispatchers.emit(setOf(dispatcher1, dispatcher2))

        dispatchManager.startDispatchLoop()

        dispatchManager.track(dispatch1)

        coVerify(timeout = 5000) {
            dispatcher1.dispatch(listOf(dispatch1))
            dispatcher2.dispatch(listOf(dispatch1))
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher2)
        }
    }

    @Test
    fun dispatchManager_ContinuesSendingDispatches_ToEnabledDispatchers_WhenOneGetsDisabled() =
        runTest {
            val dispatcher2 = spyk(TestDispatcher("dispatcher_2"))
            dispatcher1 = spyk(TestDispatcher("dispatcher_1") {
                flow {
                    dispatchers.emit(setOf(dispatcher2))
                    dispatchManager.track(dispatch2)
                    delay(100)
                    emit(it)
                }
            })
            dispatchers.emit(setOf(dispatcher1, dispatcher2))

            dispatchManager.startDispatchLoop()
            dispatchManager.track(dispatch1) // dispatcher1 removed after first dispatch

            coVerify(timeout = 5000) {
                dispatcher1.dispatch(listOf(dispatch1))
                dispatcher2.dispatch(listOf(dispatch1))
                dispatcher2.dispatch(listOf(dispatch2))
                queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
                queueManager.deleteDispatches(listOf(dispatch1), dispatcher2)
                queueManager.deleteDispatches(listOf(dispatch2), dispatcher2)
            }
            coVerify(timeout = 5000, inverse = true) {
                dispatcher1.dispatch(listOf(dispatch2))
                queueManager.deleteDispatches(listOf(dispatch2), dispatcher1)
            }
        }

    @Test
    fun dispatchManager_StopsSendingDispatchesToDispatcher_WhenDispatcherGetsDisabled() = runTest {
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") {
            flow {
                dispatchers.emit(setOf())
                dispatchManager.track(dispatch2)
                delay(100)
                emit(it)
            }
        })
        dispatchers.emit(setOf(dispatcher1))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1) // dispatcher1 removed after first dispatch

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
    fun dispatchManager_DoesNotCancelInflight_WhenDispatcherGetsDisabled() = runTest {
        dispatcher1 = spyk(TestDispatcher("dispatcher_1") {
            flow {
                dispatchers.emit(setOf())
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
}