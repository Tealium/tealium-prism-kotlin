package com.tealium.core.internal.observables

import com.tealium.core.internal.observables.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class CombineObservableTests {

    @Test
    fun combine_Emits_OnlyWhenBothHaveEmitted() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.combine(subject1, subject2, Int::times)
            .subscribe(onNext)

        subject1.onNext(5)
        verify(inverse = true) {
            onNext(any())
        }

        subject2.onNext(1)

        verify {
            onNext.invoke(5)
        }
    }

    @Test
    fun combine_EmitsAllSubsequent_WhenBothHaveEmitted() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.combine(subject1, subject2, Int::times)
            .subscribe(onNext)

        subject1.onNext(5)

        subject2.onNext(1)
        subject2.onNext(2)
        subject2.onNext(3)

        verifyOrder {
            onNext(5)
            onNext(10)
            onNext(15)
        }
    }

    @Test
    fun combine_DoesNotEmit_WhenOnlyOneHasEmitted() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.combine(subject1, subject2, Int::times)
            .subscribe(onNext)

        subject1.onNext(5)
        verify(inverse = true) {
            onNext(any())
        }
    }

    @Test
    fun combine_CanCombineMultipleTypes() {
        val subject1 = Observables.publishSubject<String>()
        val subject2 = Observables.publishSubject<Int>()
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        Observables.combine(subject1, subject2, String::plus)
            .subscribe(onNext)

        subject1.onNext("a")
        subject2.onNext(1)

        subject1.onNext("b")
        subject2.onNext(2)
        verify() {
            onNext("a1")
            onNext("b1")
            onNext("b2")
        }
    }

    @Test
    fun combine_Dispose_StopsEmitting() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = Observables.combine(subject1, subject2, Int::times)
            .subscribe(onNext)

        subject1.onNext(1)
        subject2.onNext(1)

        subscription.dispose()
        subject2.onNext(2)

        subject1.assertNoSubscribers()
        subject2.assertNoSubscribers()
        verify {
            onNext(1)
        }
        verify(inverse = true) {
            onNext(2)
        }
    }
}