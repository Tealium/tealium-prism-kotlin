package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observer
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DisposableObserverTests {

    private lateinit var delegate: Observer<Int>
    private lateinit var upstream: Subscription

    @Before
    fun setUp() {
        delegate = mockk(relaxed = true)
        upstream = Subscription()
    }

    // region onNext

    @Test
    fun onNext_Forwards_Value_When_Not_Disposed_Or_Completed() {
        val observer = DisposableObserver(delegate)

        observer.onNext(1)

        verify { delegate.onNext(1) }
    }

    @Test
    fun onNext_Does_Not_Forward_Value_When_Disposed() {
        val observer = DisposableObserver(delegate)
        observer.dispose()

        observer.onNext(1)

        verify(inverse = true) { delegate.onNext(any()) }
    }

    @Test
    fun onNext_Does_Not_Forward_Value_When_Completed() {
        val observer = DisposableObserver(delegate)
        observer.onComplete()

        observer.onNext(1)

        verify(inverse = true) { delegate.onNext(1) }
    }

    // endregion

    // region onComplete

    @Test
    fun onComplete_Invokes_HandleComplete() {
        val observer = DisposableObserver(delegate)

        observer.onComplete()

        verify { delegate.onComplete() }
    }

    @Test
    fun onComplete_Disposes_After_Completion() {
        val observer = DisposableObserver(delegate)
        observer.setUpstream(upstream)

        observer.onComplete()

        assertTrue(observer.isDisposed)
        assertTrue(upstream.isDisposed)
    }

    @Test
    fun onComplete_Is_Idempotent_When_Called_Multiple_Times() {
        val observer = DisposableObserver(delegate)

        observer.onComplete()
        observer.onComplete()
        observer.onComplete()

        verify(exactly = 1) { delegate.onComplete() }
    }

    @Test
    fun onComplete_Does_Not_Invoke_HandleComplete_When_Already_Disposed() {
        val observer = DisposableObserver(delegate)
        observer.dispose()

        observer.onComplete()

        verify(inverse = true) { delegate.onComplete() }
    }

    // endregion

    // region dispose / isDisposed

    @Test
    fun isDisposed_Is_False_Before_Any_Action() {
        val observer = DisposableObserver(delegate)

        assertFalse(observer.isDisposed)
    }

    @Test
    fun isDisposed_Is_True_After_Dispose() {
        val observer = DisposableObserver(delegate)
        observer.setUpstream(upstream)

        observer.dispose()

        assertTrue(observer.isDisposed)
    }

    @Test
    fun isDisposed_Is_True_After_OnComplete() {
        val observer = DisposableObserver(delegate)

        observer.onComplete()

        assertTrue(observer.isDisposed)
    }

    // endregion

    // region setUpstream

    @Test
    fun setUpstream_Disposes_Upstream_When_Observer_Is_Already_Disposed() {
        val observer = DisposableObserver(delegate)
        observer.dispose()

        observer.setUpstream(upstream)

        assertTrue(upstream.isDisposed)
    }

    @Test
    fun setUpstream_Disposes_Upstream_On_Observer_Dispose() {
        val observer = DisposableObserver(delegate)
        observer.setUpstream(upstream)

        observer.dispose()

        assertTrue(upstream.isDisposed)
    }

    // endregion

    // region constructors

    @Test
    fun constructor_WithObserver_Delegates_OnNext_And_OnComplete() {
        val delegate = mockk<Observer<Int>>(relaxed = true)
        val observer = DisposableObserver(delegate)

        observer.onNext(1)
        observer.onComplete()

        verify { delegate.onNext(1) }
        verify { delegate.onComplete() }
    }

    // endregion
}
