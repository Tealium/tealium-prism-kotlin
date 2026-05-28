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

class MapNotNullObservableTests {

    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        observer = mockk(relaxed = true)
    }

    @Test
    fun mapNotNull_Emits_Only_Non_Null() {
        val just = Observables.just(1, null, 2, 3, null)

        just.mapNotNull { it }
            .subscribe(observer)

        verifyOrder {
            observer.onNext(1)
            observer.onNext(2)
            observer.onNext(3)
        }
    }

    @Test
    fun mapNotNull_Dispose_Stops_Emitting() {
        val subject = Observables.publishSubject<Int?>()

        val subscription = subject.mapNotNull { it }
            .subscribe(observer)

        subject.onNext(1)
        subject.onNext(null)

        subscription.dispose()
        subject.onNext(2)

        subject.assertNoSubscribers()
        verify {
            observer.onNext(1)
        }
        verify(inverse = true) {
            observer.onNext(2)
        }
    }

    @Test
    fun mapNotNull_Emits_OnComplete_When_Source_Completes() {
        val subject = Observables.publishSubject<Int?>()
        subject.mapNotNull { it }
            .subscribe(observer)

        subject.onNext(1)
        subject.onComplete()

        verify {
            observer.onNext(1)
            observer.onComplete()
        }
    }

    @Test
    fun mapNotNull_Disposable_Is_Disposed_After_OnComplete() {
        val subject = Observables.publishSubject<Int>()
        val disposable = subject.mapNotNull { it }
            .subscribe(observer)

        assertFalse(disposable.isDisposed)

        subject.onComplete()
        assertTrue(disposable.isDisposed)
    }
}