package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DistinctObservableTests {

    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        observer = mockk(relaxed = true)
    }

    @Test
    fun distinct_Emits_Only_Non_Equal() {
        val just = Observables.just(1, 1, 2, 3, 3, 3, 4)

        just.distinct()
            .subscribe(observer)

        verifyOrder {
            observer.onNext(1)
            observer.onNext(2)
            observer.onNext(3)
            observer.onNext(4)
        }
    }

    @Test
    fun distinct_WithComparator_EmitsOnlyNonEqual() {
        val just = Observables.just(1, 1, 2, 3, 3, 3, 4)

        just.distinct { _, _ ->
            false // nothing is equal
        }.subscribe(observer)

        verifyOrder {
            observer.onNext(1)
            observer.onNext(1)
            observer.onNext(2)
            observer.onNext(3)
            observer.onNext(3)
            observer.onNext(3)
            observer.onNext(4)
        }
    }

    @Test
    fun distinct_Dispose_StopsEmitting() {
        val subject = Observables.publishSubject<Int>()

        val subscription = subject.distinct()
            .subscribe(observer)

        subject.onNext(1)
        subject.onNext(2)
        subscription.dispose()
        subject.onNext(3)

        subject.assertNoSubscribers()
        verify {
            observer.onNext(1)
            observer.onNext(2)
        }
        verify(inverse = true) {
            observer.onNext(3)
        }
    }

    @Test
    fun distinct_Emits_OnComplete_When_Source_Completes() {
        val subject = Observables.publishSubject<Int>()
        subject.distinct()
            .subscribe(observer)

        subject.onNext(1)
        subject.onComplete()

        verify {
            observer.onNext(1)
            observer.onComplete()
        }
    }

    @Test
    fun distinct_Disposable_Is_Disposed_After_OnComplete() {
        val subject = Observables.publishSubject<Int>()
        val disposable = subject.distinct()
            .subscribe(observer)

        assertFalse(disposable.isDisposed)

        subject.onComplete()
        assertTrue(disposable.isDisposed)
    }
}