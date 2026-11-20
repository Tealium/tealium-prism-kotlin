package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.api.tracking.TrackResultListener
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.tests.common.AsyncTransformer
import com.tealium.tests.common.PendingStep
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DispatchManagerTransformerTests : DispatchManagerTestsBase() {

    @Test
    fun dispatchManager_DoesNotSendDispatchesToDispatcher_WhenTransformersReturnNull() {
        registerTransformation(scope = TransformationScope.AllDispatchers) { _, _, _ -> null }

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(exactly = 0) {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }
    }

    @Test
    fun dispatchManager_DoesNotQueue_WhenTransformersReturnNull() {
        registerTransformation(scope = TransformationScope.AfterCollectors) { _, _, _ -> null }

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        verify(exactly = 0) {
            dispatcher1.dispatch(listOf(dispatch1), any())
            queueManager.storeDispatches(listOf(dispatch1), any())
        }
    }

    @Test
    fun dispatchManager_Notifies_DispatchDropped_WhenDispatchDropped_AfterCollectors() {
        registerTransformation(scope = TransformationScope.AfterCollectors) { _, _, _ -> null }
        val onComplete: (TrackResult) -> Unit = mockk(relaxed = true)

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1, onComplete)

        verify(timeout = 1000) {
            onComplete(match {
                it.status == TrackResult.Status.Dropped
            })
        }
    }

    @Test
    fun dispatchManager_Disposes_Transformation_When_Dispatch_Loop_Is_Stopped() {
        val (asyncTransformer, step) = startPendingTransformation()

        dispatchManager.stopDispatchLoop()

        assertTrue(step.disposable.isDisposed)
        assertEquals(listOf(step), asyncTransformer.pending)
    }

    @Test
    fun dispatchManager_Does_Not_Dispatch_When_Stopped_During_Transformation() {
        val (asyncTransformer, step) = startPendingTransformation()

        dispatchManager.stopDispatchLoop()
        asyncTransformer.complete(step, dispatch1)

        verify(exactly = 0) {
            dispatcher1.dispatch(any(), any())
        }
    }

    /**
     * [DispatchManagerImpl.transformAndDispatch] sits downstream of the `flatMapLatest` that rebuilds
     * the dequeue loops, so a change to the dispatcher list leaves a running transformation alone and
     * its batch is still sent.
     */
    @Test
    fun dispatchManager_Does_Not_Cancel_Transformation_When_Dispatcher_List_Changes() {
        val (asyncTransformer, step) = startPendingTransformation()

        dispatchers.onNext(listOf(dispatcher1, dispatcher2))

        assertFalse(step.disposable.isDisposed)

        asyncTransformer.complete(step, dispatch1)

        verify(exactly = 1) {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }
    }

    /**
     * [DispatchManagerImpl.track] relies on the per-step once-guard of the
     * [TransformerCoordinator]: a transformer that completes the same step twice must neither
     * store the dispatch nor report its result a second time.
     */
    @Test
    fun dispatchManager_Calls_OnTrackResult_Once_When_Transformer_Completes_Twice() {
        val asyncTransformer = AsyncTransformer("async")
        registerTransformation(asyncTransformer, scope = TransformationScope.AfterCollectors)
        val listener = mockk<TrackResultListener>(relaxed = true)

        dispatchManager.track(dispatch1, listener)
        assertEquals(1, asyncTransformer.pending.size)
        val step = asyncTransformer.pending.single()

        asyncTransformer.complete(step, dispatch1)
        asyncTransformer.completeAgain(step, dispatch1)

        verify(exactly = 1) {
            listener.onTrackResultReady(any())
        }
        verify(exactly = 1) {
            queueManager.storeDispatches(listOf(dispatch1), any())
        }
    }

    private data class PendingBatch(
        val transformer: AsyncTransformer,
        val step: PendingStep
    )

    /**
     * Starts the loop with [dispatcher1] alone and tracks [dispatch1], so that it is dequeued and
     * held by an [AsyncTransformer] scoped to [dispatcher1], then returns that transformer and its
     * single pending step.
     */
    private fun startPendingTransformation(): PendingBatch {
        val asyncTransformer = AsyncTransformer("async")
        registerTransformation(
            asyncTransformer,
            scope = TransformationScope.Dispatchers(listOf(dispatcher1Name))
        )
        dispatchers.onNext(listOf(dispatcher1))

        dispatchManager.startDispatchLoop()
        dispatchManager.track(dispatch1)

        assertEquals(1, asyncTransformer.pending.size)
        return PendingBatch(asyncTransformer, asyncTransformer.pending.single())
    }
}