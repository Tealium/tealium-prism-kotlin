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
import kotlin.math.abs

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
    fun distinct_Does_Not_Re_Emit_Null_When_Previous_Value_Was_Null() {
        val nullableObserver = mockk<Observer<Int?>>(relaxed = true)

        Observables.just(null, null, 1)
            .distinct()
            .subscribe(nullableObserver)

        verify(exactly = 1) {
            nullableObserver.onNext(null)
        }
        verify(exactly = 1) {
            nullableObserver.onNext(1)
        }
    }

    @Test
    fun distinct_Emits_Null_After_Non_Null_Value() {
        val nullableObserver = mockk<Observer<Int?>>(relaxed = true)

        Observables.just(1, null, null, 1)
            .distinct()
            .subscribe(nullableObserver)

        verifyOrder {
            nullableObserver.onNext(1)
            nullableObserver.onNext(null)
            nullableObserver.onNext(1)
        }
        verify(exactly = 1) {
            nullableObserver.onNext(null)
        }
        verify(exactly = 2) {
            nullableObserver.onNext(1)
        }
    }

    @Test
    fun distinct_Compares_With_Previous_Element_When_Previous_Was_Suppressed() {
        Observables.just(0, 1, 2, 3)
            .distinct(::isWithinOne)
            .subscribe(observer)

        verify(exactly = 1) {
            observer.onNext(0)
        }
        verify(exactly = 1) {
            observer.onNext(any())
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

    /**
     * Not transitive: 0 ~ 1 and 1 ~ 2, but not 0 ~ 2.
     */
    private fun isWithinOne(a: Int, b: Int): Boolean =
        abs(a - b) <= 1
}