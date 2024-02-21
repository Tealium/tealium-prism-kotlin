package com.tealium.core.internal.observables

import com.tealium.core.internal.observables.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class MergeObservableTests {

    @Test
    fun merge_EmitsAll_FromAllObservables_InOrder() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.just(1, 2).merge(
            Observables.just(3, 4)
        ).subscribe(onNext)

        verifyOrder {
            onNext(1)
            onNext(2)

            onNext(3)
            onNext(4)
        }
    }

    @Test
    fun merge_EmitsAll_FromAllObservables() {
        val publishSubject = Observables.publishSubject<Int>()
        val replaySubject = Observables.replaySubject<Int>(1)
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        publishSubject.onNext(1) // missed.
        replaySubject.onNext(2)

        publishSubject.merge(
            replaySubject
        ).subscribe(onNext)

        publishSubject.onNext(3)
        replaySubject.onNext(4)

        verifyOrder {
            onNext(2)
            onNext(3)
            onNext(4)
        }
    }

    @Test
    fun merge_Static_EmitsAll_FromAllObservables_InOrder() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.merge(
            Observables.just(1, 2),
            Observables.just(3, 4),
            Observables.just(5, 6)
        ).subscribe(onNext)

        verifyOrder {
            onNext(1)
            onNext(2)

            onNext(3)
            onNext(4)

            onNext(5)
            onNext(6)
        }
    }

    @Test
    fun merge_Static_EmitsAll_FromAllObservables() {
        val publishSubject = Observables.publishSubject<Int>()
        val replaySubject = Observables.replaySubject<Int>(1)
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        publishSubject.onNext(1) // missed.
        replaySubject.onNext(2)

        Observables.merge(
            publishSubject,
            replaySubject
        ).subscribe(onNext)

        publishSubject.onNext(3)
        replaySubject.onNext(4)

        verifyOrder {
            onNext(2)
            onNext(3)
            onNext(4)
        }
    }

    @Test
    fun merge_Dispose_StopsEmitting() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = subject1.merge(
            subject2
        ).subscribe(onNext)

        subject1.onNext(1)
        subject2.onNext(2)
        subscription.dispose()
        subject1.onNext(3)
        subject2.onNext(4)

        subject1.assertNoSubscribers()
        subject2.assertNoSubscribers()
        verify {
            onNext(1)
            onNext(2)
        }
        verify(inverse = true) {
            onNext(3)
            onNext(4)
        }
    }

    @Test
    fun merge_Static_Dispose_StopsEmitting() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = Observables.merge(
            subject1,
            subject2
        ).subscribe(onNext)

        subject1.onNext(1)
        subject2.onNext(2)
        subscription.dispose()
        subject1.onNext(3)
        subject2.onNext(4)

        subject1.assertNoSubscribers()
        subject2.assertNoSubscribers()
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