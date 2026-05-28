package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Consumer
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnonymousObserverTests {

    private lateinit var handleNext: Consumer<Int>
    private lateinit var handleComplete: Runnable
    private lateinit var upstream: Subscription

    @Before
    fun setUp() {
        handleNext = mockk(relaxed = true)
        handleComplete = mockk(relaxed = true)
        upstream = Subscription()
    }

    // region onNext

    @Test
    fun onNext_Forwards_Value_When_Not_Disposed_Or_Completed() {
        val observer = AnonymousObserver(handleNext, handleComplete)

        observer.onNext(1)

        verify { handleNext.accept(1) }
    }

    @Test
    fun onNext_Does_Not_Forward_Value_When_Disposed() {
        val observer = AnonymousObserver(handleNext, handleComplete)
        observer.dispose()

        observer.onNext(1)

        verify(inverse = true) { handleNext.accept(any()) }
    }

    @Test
    fun onNext_Does_Not_Forward_Value_When_Completed() {
        val observer = AnonymousObserver(handleNext, handleComplete)
        observer.onComplete()

        observer.onNext(1)

        verify(inverse = true) { handleNext.accept(any()) }
    }

    // endregion

    // region onComplete

    @Test
    fun onComplete_Invokes_HandleComplete() {
        val observer = AnonymousObserver(handleNext, handleComplete)

        observer.onComplete()

        verify { handleComplete.run() }
    }

    @Test
    fun onComplete_Disposes_After_Completion() {
        val observer = AnonymousObserver(handleNext, handleComplete)
        observer.setUpstream(upstream)

        observer.onComplete()

        assertTrue(observer.isDisposed)
        assertTrue(upstream.isDisposed)
    }

    @Test
    fun onComplete_Is_Idempotent_When_Called_Multiple_Times() {
        val observer = AnonymousObserver(handleNext, handleComplete)

        observer.onComplete()
        observer.onComplete()
        observer.onComplete()

        verify(exactly = 1) { handleComplete.run() }
    }

    @Test
    fun onComplete_Does_Not_Invoke_HandleComplete_When_Already_Disposed() {
        val observer = AnonymousObserver(handleNext, handleComplete)
        observer.dispose()

        observer.onComplete()
        observer.onComplete()

        verify(exactly = 0) { handleComplete.run() }
    }

    // endregion

    // region dispose / isDisposed

    @Test
    fun isDisposed_Is_False_Before_Any_Action() {
        val observer = AnonymousObserver(handleNext, handleComplete)

        assertFalse(observer.isDisposed)
    }

    @Test
    fun isDisposed_Is_True_After_Dispose() {
        val observer = AnonymousObserver(handleNext, handleComplete)
        observer.setUpstream(upstream)

        observer.dispose()

        assertTrue(observer.isDisposed)
    }

    @Test
    fun isDisposed_Is_True_After_OnComplete() {
        val observer = AnonymousObserver(handleNext, handleComplete)

        observer.onComplete()

        assertTrue(observer.isDisposed)
    }

    // endregion

    // region setUpstream

    @Test
    fun setUpstream_Disposes_Upstream_When_Observer_Is_Already_Disposed() {
        val observer = AnonymousObserver(handleNext, handleComplete)
        observer.dispose()

        observer.setUpstream(upstream)

        assertTrue(upstream.isDisposed)
    }

    @Test
    fun setUpstream_Disposes_Upstream_On_Observer_Dispose() {
        val observer = AnonymousObserver(handleNext, handleComplete)
        observer.setUpstream(upstream)

        observer.dispose()

        assertTrue(upstream.isDisposed)
    }

    // endregion

    // region constructors

    @Test
    fun constructor_WithConsumerOnly_Does_Not_Fail_On_OnComplete() {
        val observer = AnonymousObserver(handleNext)

        observer.onNext(1)
        observer.onComplete()

        verify { handleNext.accept(1) }
        // no assertion on handleComplete -- there isn't one; just verifying no exception
    }

    // endregion
}