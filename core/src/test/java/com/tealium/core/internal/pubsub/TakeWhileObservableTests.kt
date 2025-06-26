package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.Observables
import com.tealium.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class TakeWhileObservableTests {

    @Test
    fun takeWhile_Emits_Only_Configured_Amount() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.just(1, 2, 3, 2)
            .takeWhile { it <= 2 }
            .subscribe(onNext)

        verify(exactly = 1) {
            onNext(1)
            onNext(2)
        }

        confirmVerified(onNext)
    }

    @Test
    fun takeWhile_Emits_Final_Emission_When_Inclusive() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.just(1, 2, 3, 2)
            .takeWhile(inclusive = true) { it <= 2 }
            .subscribe(onNext)

        verify(exactly = 1) {
            onNext(1)
            onNext(2)
            onNext(3)
        }

        confirmVerified(onNext)
    }

    @Test
    fun takeWhile_Auto_Disposes_When_Predicate_Fails() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        subject.takeWhile {
            it <= 2
        }.subscribe(onNext)

        subject.onNext(1)
        subject.onNext(2)
        subject.onNext(3) // auto-dispose

        subject.assertNoSubscribers()
        verify {
            onNext(1)
            onNext(2)
        }
        confirmVerified(onNext)
    }

    @Test
    fun takeWhile_Dispose_Stops_Emitting() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = subject.takeWhile {
            it <= 2
        }.subscribe(onNext)

        subject.onNext(1)
        subscription.dispose()
        subject.onNext(2)

        subject.assertNoSubscribers()
        verify {
            onNext(1)
        }
        confirmVerified(onNext)
    }
}