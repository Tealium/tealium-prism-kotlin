package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.Observables
import com.tealium.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class FlatMapObservableTests {

    @Test
    fun flatMap_TransformsEmissions_FromAllObservables() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.just(1, 2, 3)
            .flatMap {
                Observables.just(it, it + 1)
            }
            .subscribe(onNext)

        verifyOrder {
            onNext(1)
            onNext(2)

            onNext(2)
            onNext(3)

            onNext(3)
            onNext(4)
        }
    }

    @Test
    fun flatMap_TransformsEmissions_FromAllObservables_InOrder() {
        val subject = Observables.publishSubject<Int>()

        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        subject.flatMap {
            Observables.just(it, it + 1)
        }.subscribe(onNext)

        subject.onNext(1)
        subject.onNext(2)
        subject.onNext(3)

        verify {
            onNext(1)
            onNext(2)

            onNext(2)
            onNext(3)

            onNext(3)
            onNext(4)
        }
    }

    @Test
    fun flatMap_Dispose_StopsEmitting() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = subject.flatMap {
            Observables.just(it, it + 1)
        }.subscribe(onNext)

        subject.onNext(1)
        subscription.dispose()
        subject.onNext(3)

        subject.assertNoSubscribers()
        verify {
            onNext(1)
            onNext(2)
        }
        verify(inverse = true) {
            onNext(3)
            onNext(4)
        }
    }
}