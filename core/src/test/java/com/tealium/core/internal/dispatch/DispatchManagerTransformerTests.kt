package com.tealium.core.internal.dispatch

import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.api.transform.TransformationScope
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class DispatchManagerTransformerTests : DispatchManagerTestsBase() {

    @Test
    fun dispatchManager_DoesNotSendDispatchesToDispatcher_WhenTransformersReturnNull() {
        registerTransformation(scope = setOf(TransformationScope.AllDispatchers)) { _, _, _ -> null }

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(timeout = 5000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }
    }

    @Test
    fun dispatchManager_DoesNotQueue_WhenTransformersReturnNull() {
        registerTransformation(scope = setOf(TransformationScope.AfterCollectors)) { _, _, _ -> null }

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(timeout = 5000, inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.storeDispatches(listOf(dispatch1), any())
        }
    }

    @Test
    fun dispatchManager_Notifies_DispatchDropped_WhenDispatchDropped_AfterCollectors() {
        registerTransformation(scope = setOf(TransformationScope.AfterCollectors)) { _, _, _ -> null }
        val onComplete: (TrackResult) -> Unit = mockk(relaxed = true)

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1, onComplete)

        verify(timeout = 1000) {
            onComplete(match {
                it.status == TrackResult.Status.Dropped
            })
        }
    }
}