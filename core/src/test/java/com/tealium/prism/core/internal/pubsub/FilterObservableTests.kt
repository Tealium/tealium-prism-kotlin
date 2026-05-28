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

class FilterObservableTests {

    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        observer = mockk(relaxed = true)
    }

    private fun isEven(number: Int) : Boolean {
        return number % 2 == 0
    }

    @Test
    fun filter_Emits_Only_Items_That_Satisfy_Filter() {
        Observables.just(1, 2, 3, 4)
            .filter(::isEven)
            .subscribe(observer)

        verifyOrder {
            observer.onNext(2)
            observer.onNext(4)
        }
        verify(inverse = true) {
            observer.onNext(1)
            observer.onNext(3)
        }
    }

    @Test
    fun filter_Dispose_Stops_Emitting() {
        val subject = Observables.publishSubject<Int>()

        val subscription = subject.filter(::isEven)
            .subscribe(observer)

        subject.onNext(1)
        subject.onNext(2)
        subscription.dispose()
        subject.onNext(3)
        subject.onNext(4)


        subject.assertNoSubscribers()
        verify {
            observer.onNext(2)
        }
        verify(inverse = true) {
            observer.onNext(1)
            observer.onNext(3)
            observer.onNext(4)
        }
    }

    @Test
    fun filter_Emits_OnComplete_When_Source_Completes() {
        val subject = Observables.publishSubject<Int>()
        subject.filter { true }
            .subscribe(observer)

        subject.onNext(1)
        subject.onComplete()

        verify {
            observer.onNext(1)
            observer.onComplete()
        }
    }

    @Test
    fun filter_Disposable_Is_Disposed_After_OnComplete() {
        val subject = Observables.publishSubject<Int>()
        val disposable = subject.filter { true }
            .subscribe(observer)

        assertFalse(disposable.isDisposed)

        subject.onComplete()
        assertTrue(disposable.isDisposed)
    }
}