package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.Observables
import com.tealium.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class MapNotNullObservableTests {

    @Test
    fun mapNotNull_EmitsOnlyNonNull() {
        val just = Observables.just(1, null, 2, 3, null)
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        just.mapNotNull { it }
            .subscribe(onNext)

        verifyOrder {
            onNext(1)
            onNext(2)
            onNext(3)
        }
    }

    @Test
    fun mapNotNull_Dispose_StopsEmitting() {
        val subject = Observables.publishSubject<Int?>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = subject.mapNotNull {
            it
        }.subscribe(onNext)

        subject.onNext(1)
        subject.onNext(null)

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