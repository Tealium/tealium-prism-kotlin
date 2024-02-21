package com.tealium.core.internal.observables

import com.tealium.core.internal.observables.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class DistinctObservableTests {

    @Test
    fun distinct_EmitsOnlyNonEqual() {
        val just = Observables.just(1, 1, 2, 3, 3, 3, 4)
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        just.distinct()
            .subscribe(onNext)

        verifyOrder {
            onNext.invoke(1)
            onNext.invoke(2)
            onNext.invoke(3)
            onNext.invoke(4)
        }
    }

    @Test
    fun distinct_WithComparator_EmitsOnlyNonEqual() {
        val just = Observables.just(1, 1, 2, 3, 3, 3, 4)
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        just.distinct { _, _ ->
            false // nothing is equal
        }.subscribe(onNext)

        verifyOrder {
            onNext.invoke(1)
            onNext.invoke(1)
            onNext.invoke(2)
            onNext.invoke(3)
            onNext.invoke(3)
            onNext.invoke(3)
            onNext.invoke(4)
        }
    }

    @Test
    fun distinct_Dispose_StopsEmitting() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = subject.distinct()
            .subscribe(onNext)

        subject.onNext(1)
        subject.onNext(2)
        subscription.dispose()
        subject.onNext(3)

        subject.assertNoSubscribers()
        verify {
            onNext(1)
            onNext(2)
        }
        verify(inverse = true) {
            onNext(3)
        }
    }
}