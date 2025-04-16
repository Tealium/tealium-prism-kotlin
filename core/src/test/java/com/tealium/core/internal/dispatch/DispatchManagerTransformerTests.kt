package com.tealium.core.internal.dispatch

import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.api.transform.TransformationSettings
import com.tealium.core.api.transform.TransformationScope
import com.tealium.core.api.transform.Transformer
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test

class DispatchManagerTransformerTests : DispatchManagerTestsBase() {

    @MockK
    private lateinit var droppingTransformer: Transformer

    override fun onAfterSetup() {
        val completionCapture = slot<(Dispatch?) -> Unit>()
        every { droppingTransformer.id } returns "drop"
        every {
            droppingTransformer.applyTransformation(
                any(),
                any(),
                any(),
                capture(completionCapture)
            )
        } answers {
            completionCapture.captured(null)
        }

        transformers.onNext(transformers.value + droppingTransformer)
    }

    @Test
    fun dispatchManager_DoesNotSendDispatchesToDispatcher_WhenTransformersReturnNull() {
        transformations.onNext(
            setOf(
                TransformationSettings("drop", "drop", setOf(TransformationScope.AllDispatchers))
            )
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(timeout = 5000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }
    }

    @Test
    fun dispatchManager_DoesNotQueue_WhenTransformersReturnNull() {
        transformations.onNext(
            setOf(
                TransformationSettings("drop", "drop", setOf(TransformationScope.AfterCollectors))
            )
        )

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(timeout = 5000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.storeDispatches(listOf(dispatch1), any())
        }
    }

    @Test
    fun dispatchManager_Notifies_DispatchDropped_WhenDispatchDropped_AfterCollectors() {
        transformations.onNext(
            setOf(
                TransformationSettings("drop", "drop", setOf(TransformationScope.AfterCollectors))
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