package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.tests.common.assertThrows
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TakeObservableTests {

    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        observer = mockk(relaxed = true)
    }

    @Test
    fun take_Emits_Only_Configured_Amount_Then_Completes() {
        Observables.just(1, 2, 3)
            .take(2)
            .subscribe(observer)

        verify {
            observer.onNext(1)
            observer.onNext(2)
            observer.onComplete()
        }
        verify(inverse = true) {
            observer.onNext(3)
        }
    }

    @Test
    fun take_Automatically_Disposes_And_Stops_Emitting_And_Completes() {
        val subject = Observables.publishSubject<Int>()

        val disposable = subject.take(1)
            .subscribe(observer)

        subject.onNext(1)
        assertTrue(disposable.isDisposed)

        subject.onNext(2)

        subject.assertNoSubscribers()
        verify {
            observer.onNext(1)
            observer.onComplete()
        }
        verify(inverse = true) {
            observer.onNext(2)
        }
    }

    @Test
    fun take_Dispose_Stops_Emitting() {
        val subject = Observables.publishSubject<Int>()

        val subscription = subject.take(2)
            .subscribe(observer)

        subject.onNext(1)
        subscription.dispose()
        subject.onNext(2)


        subject.assertNoSubscribers()
        verify {
            observer.onNext(1)
        }
        verify(inverse = true) {
            observer.onNext(2)
            observer.onComplete()
        }
    }

    @Test
    fun take_Stops_Reentrant_Events_And_Completes() {
        val subject = Observables.publishSubject<Int>()
        every { observer.onNext(any()) } answers {
            subject.onNext(firstArg<Int>() + 1) // reentrantly emit 2
        }

        subject.take(1)
            .subscribe(observer)

        subject.onNext(1)

        subject.assertNoSubscribers()
        verify {
            observer.onNext(1)
            observer.onComplete()
        }
        verify(inverse = true) {
            observer.onNext(2)
        }
    }

    @Test
    fun init_Throws_When_Count_Is_Less_Than_Or_Equal_to_Zero() {
        val observable = Observables.just(1)

        assertThrows<IllegalArgumentException> {
            observable.take(0)
        }
        assertThrows<IllegalArgumentException> {
            observable.take(-1)
        }
    }
}