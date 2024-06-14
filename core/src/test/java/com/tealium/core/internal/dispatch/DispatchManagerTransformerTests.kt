package com.tealium.core.internal.dispatch

import com.tealium.core.api.Dispatch
import com.tealium.core.api.TrackResult
import com.tealium.core.api.transformations.ScopedTransformation
import com.tealium.core.api.transformations.TransformationScope
import com.tealium.core.api.transformations.Transformer
import io.mockk.every
import io.mockk.verify
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import org.junit.Test

class DispatchManagerTransformerTests : DispatchManagerTestsBase() {

    @MockK
    private lateinit var droppingTransformer: Transformer

    override fun onAfterSetup() {
        val completionCapture = slot<(Dispatch?) -> Unit>()
        every { droppingTransformer.id } returns "drop"
        every { droppingTransformer.applyTransformation(any(), any(), any(), capture(completionCapture)) } answers {
            completionCapture.captured(null)
        }

        transformers.add(droppingTransformer)
    }

    @Test
    fun dispatchManager_DoesNotSendDispatchesToDispatcher_WhenTransformersReturnNull() {
        transformersFlow.onNext(
            setOf(
                ScopedTransformation("drop", "drop", setOf(TransformationScope.AllDispatchers))
            )
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(timeout = 5000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1))
        }
    }

    @Test
    fun dispatchManager_DoesNotQueue_WhenTransformersReturnNull() {
        transformersFlow.onNext(
            setOf(
                ScopedTransformation("drop", "drop", setOf(TransformationScope.AfterCollectors))
            )
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(timeout = 5000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1))
            queueManager.storeDispatches(listOf(dispatch1), any())
        }
    }

    @Test
    fun dispatchManager_Notifies_DispatchDropped_WhenDispatchDropped_AfterCollectors() {
        transformersFlow.onNext(
            setOf(
                ScopedTransformation("drop", "drop", setOf(TransformationScope.AfterCollectors))
            )
        )
        val onComplete: (Dispatch, TrackResult) -> Unit = mockk(relaxed = true)

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1, onComplete)

        verify(timeout = 1000) {
            onComplete(dispatch1, TrackResult.Dropped)
        }
    }
}