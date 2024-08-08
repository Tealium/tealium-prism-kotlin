package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.Observables
import com.tealium.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class BufferedObservableTests {

    @Test
    fun buffered_EmitsAll_WhenCountIsReached() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        subject.buffered(3)
            .subscribe(onNext)

        subject.onNext(1)
        subject.onNext(2)
        subject.onNext(3)

        verify {
            onNext(1)
            onNext(2)
            onNext(3)
        }
    }

    @Test
    fun buffered_DoesNotEmit_WhenCountNotReached() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)
        subject.buffered(3)
            .subscribe(onNext)

        subject.onNext(1)
        subject.onNext(2)

        verify(inverse = true) {
            onNext(any())
        }
    }

    @Test
    fun buffered_Dispose_StopsEmitting() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = subject.buffered(3)
            .subscribe(onNext)

        subject.onNext(1)
        subject.onNext(2)

        subscription.dispose()
        subject.onNext(3)

        subject.assertNoSubscribers()
        verify(inverse = true) {
            onNext(1)
            onNext(2)
            onNext(3)
        }
    }
}