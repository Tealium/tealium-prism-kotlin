package com.tealium.core.internal.dispatch

import com.tealium.core.api.Transformer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DispatchManagerTransformerTests : DispatchManagerTestsBase() {

    @MockK
    private lateinit var droppingTransformer: Transformer

    override fun onAfterSetup() {
        coEvery { droppingTransformer.id } returns "drop"
        coEvery { droppingTransformer.applyTransformation(any(), any(), any()) } returns null

        transformers.add(droppingTransformer)
    }

    @Test
    fun dispatchManager_DoesNotSendDispatchesToDispatcher_WhenTransformersReturnNull() = runTest {
        transformersFlow.emit(
            setOf(
                ScopedTransformation("drop", "drop", setOf(TransformationScope.AllDispatchers))
            )
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        coVerify(timeout = 5000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1))
        }
        coVerify(timeout = 5000) {
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)
        }
    }

    @Test
    fun dispatchManager_DoesNotQueue_WhenTransformersReturnNull() = runTest {
        transformersFlow.emit(
            setOf(
                ScopedTransformation("drop", "drop", setOf(TransformationScope.AfterCollectors))
            )
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        coVerify(timeout = 5000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1))
            queueManager.storeDispatch(dispatch1, any())
        }
    }
}