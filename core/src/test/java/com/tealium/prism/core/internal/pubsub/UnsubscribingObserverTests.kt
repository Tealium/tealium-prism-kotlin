package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observer
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UnsubscribingObserverTests {

    private lateinit var ownerList: MutableList<com.tealium.prism.core.api.pubsub.Disposable>
    private lateinit var owner: DisposableContainer
    private lateinit var delegate: Observer<Int>
    private lateinit var upstream: Subscription

    @Before
    fun setUp() {
        ownerList = mutableListOf()
        owner = DisposableContainer(ownerList)
        delegate = mockk(relaxed = true)
        upstream = Subscription()
    }

    // region onNext

    @Test
    fun onNext_Forwards_Value_To_Delegate_When_Not_Disposed_Or_Completed() {
        val observer = UnsubscribingObserver(owner, delegate)

        observer.onNext(1)

        verify { delegate.onNext(1) }
    }

    @Test
    fun onNext_Does_Not_Forward_Value_When_Disposed() {
        val observer = UnsubscribingObserver(owner, delegate)
        observer.dispose()

        observer.onNext(1)

        verify(inverse = true) { delegate.onNext(any()) }
    }

    @Test
    fun onNext_Does_Not_Forward_Value_When_Completed() {
        val observer = UnsubscribingObserver(owner, delegate)
        observer.onComplete()

        observer.onNext(1)

        verify(inverse = true) { delegate.onNext(any()) }
    }

    // endregion

    // region onComplete

    @Test
    fun onComplete_Forwards_To_Delegate() {
        val observer = UnsubscribingObserver(owner, delegate)

        observer.onComplete()

        verify { delegate.onComplete() }
    }

    @Test
    fun onComplete_Removes_Self_From_Owner() {
        val observer = UnsubscribingObserver(owner, delegate)
        owner.add(observer)

        observer.onComplete()

        assertFalse(ownerList.contains(observer))
    }

    @Test
    fun onComplete_Disposes_Self_And_Upstream() {
        val observer = UnsubscribingObserver(owner, delegate)
        observer.setUpstream(upstream)

        observer.onComplete()

        assertTrue(observer.isDisposed)
        assertTrue(upstream.isDisposed)
    }

    @Test
    fun onComplete_Is_Idempotent_When_Called_Multiple_Times() {
        val observer = UnsubscribingObserver(owner, delegate)

        observer.onComplete()
        observer.onComplete()
        observer.onComplete()

        verify(exactly = 1) { delegate.onComplete() }
    }

    // endregion

    // region dispose / isDisposed

    @Test
    fun isDisposed_Is_False_Before_Any_Action() {
        val observer = UnsubscribingObserver(owner, delegate)

        assertFalse(observer.isDisposed)
    }

    @Test
    fun isDisposed_Is_True_After_Dispose() {
        val observer = UnsubscribingObserver(owner, delegate)
        observer.setUpstream(upstream)

        observer.dispose()

        assertTrue(observer.isDisposed)
    }

    @Test
    fun isDisposed_Is_True_After_OnComplete() {
        val observer = UnsubscribingObserver(owner, delegate)

        observer.onComplete()

        assertTrue(observer.isDisposed)
    }

    @Test
    fun dispose_Removes_Self_From_Owner() {
        val observer = UnsubscribingObserver(owner, delegate)
        owner.add(observer)

        observer.dispose()

        assertFalse(ownerList.contains(observer))
    }

    @Test
    fun dispose_Does_Not_Notify_Delegate_OnComplete() {
        val observer = UnsubscribingObserver(owner, delegate)

        observer.dispose()

        verify(inverse = true) { delegate.onComplete() }
    }

    // endregion

    // region setUpstream

    @Test
    fun setUpstream_Disposes_Upstream_When_Observer_Is_Already_Disposed() {
        val observer = UnsubscribingObserver(owner, delegate)
        observer.dispose()

        observer.setUpstream(upstream)

        assertTrue(upstream.isDisposed)
    }

    @Test
    fun setUpstream_Disposes_Upstream_On_Observer_Dispose() {
        val observer = UnsubscribingObserver(owner, delegate)
        observer.setUpstream(upstream)

        observer.dispose()

        assertTrue(upstream.isDisposed)
    }

    // endregion
}
