package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class MapObservableTests {

    @Test
    fun map_TransformsEmissions() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Long) -> Unit>(relaxed = true)

        subject.map(Int::toLong)
            .subscribe(onNext)

        subject.onNext(1)
        verify {
            onNext(1L)
        }
    }

    @Test
    fun map_TransformsEmissions_AndCanBeChained() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Long) -> Unit>(relaxed = true)

        subject.map(Int::toLong)
            .map(Long::inc) // 6
            .map(Long::inc) // 7
            .subscribe(onNext)

        subject.onNext(1)

        verify {
            onNext(3L)
        }
    }

    @Test
    fun map_Dispose_StopsEmitting() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = subject.map(Int::inc)
            .subscribe(onNext)

        subject.onNext(1)

        subscription.dispose()
        subject.onNext(2)

        subject.assertNoSubscribers()
        verify {
            onNext(2)
        }
        verify(inverse = true) {
            onNext(3)
        }
    }
}