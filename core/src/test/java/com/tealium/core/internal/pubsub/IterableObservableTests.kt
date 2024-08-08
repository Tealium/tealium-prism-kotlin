package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.Observables
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.Test

class IterableObservableTests {

    @Test
    fun just_EmitsAll_OnSubscribe() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.just(1, 2, 3, 4, 5)
            .subscribe(onNext)

        verifyOrder {
            onNext(1)
            onNext(2)
            onNext(3)
            onNext(4)
            onNext(5)
        }
    }

    @Test
    fun just_Single_EmitsOne_OnSubscribe() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.just(1)
            .subscribe(onNext)

        verifyOrder {
            onNext(1)
        }
    }
}