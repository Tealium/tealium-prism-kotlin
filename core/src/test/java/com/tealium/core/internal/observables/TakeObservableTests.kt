package com.tealium.core.internal.observables

import com.tealium.core.internal.observables.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeObservableTests {

    @Test
    fun take_EmitsOnly_ConfiguredAmount() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.just(1, 2, 3)
            .take(2)
            .subscribe(onNext)

        verify {
            onNext(1)
            onNext(2)
        }
        verify(inverse = true) {
            onNext(3)
        }
    }

    @Test
    fun take_AutomaticallyDisposes_And_StopsEmitting() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val disposable = subject.take(1)
            .subscribe(onNext)

        subject.onNext(1)
        assertTrue(disposable.isDisposed)

        subject.onNext(2)

        subject.assertNoSubscribers()
        verify {
            onNext(1)
        }
        verify(inverse = true) {
            onNext(2)
        }
    }

    @Test
    fun take_Dispose_StopsEmitting() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = subject.take(2)
            .subscribe(onNext)

        subject.onNext(1)
        subscription.dispose()
        subject.onNext(2)


        subject.assertNoSubscribers()
        verify {
            onNext(1)
        }
        verify(inverse = true) {
            onNext(2)
        }
    }
}