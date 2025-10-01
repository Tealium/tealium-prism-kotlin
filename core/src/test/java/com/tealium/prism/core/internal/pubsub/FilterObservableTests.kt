package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class FilterObservableTests {

    private fun isEven(number: Int) : Boolean {
        return number % 2 == 0
    }

    @Test
    fun filter_EmitsOnly_ItemsTheSatisfyFilter() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.just(1, 2, 3, 4)
            .filter(::isEven)
            .subscribe(onNext)

        verifyOrder {
            onNext(2)
            onNext(4)
        }
        verify(inverse = true) {
            onNext(1)
            onNext(3)
        }
    }


    @Test
    fun filter_Dispose_StopsEmitting() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = subject.filter(::isEven)
            .subscribe(onNext)

        subject.onNext(1)
        subject.onNext(2)
        subscription.dispose()
        subject.onNext(3)
        subject.onNext(4)


        subject.assertNoSubscribers()
        verify {
            onNext(2)
        }
        verify(inverse = true) {
            onNext(1)
            onNext(3)
            onNext(4)
        }
    }
}