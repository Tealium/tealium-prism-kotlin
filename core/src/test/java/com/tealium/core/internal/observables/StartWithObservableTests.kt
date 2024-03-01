package com.tealium.core.internal.observables

import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.Test

class StartWithObservableTests {

    @Test
    fun startWith_EmitsGivenItems_BeforeNextOne() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        subject.startWith(1, 2, 3)
            .subscribe(onNext)
        subject.onNext(4)

        verifyOrder {
            onNext(1)
            onNext(2)
            onNext(3)
            onNext(4)
        }
    }

    @Test
    fun startWith_EmitsGivenItems_BeforeSubjectsState() {
        val subject = Observables.stateSubject<Int>(4)
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        subject.startWith(1, 2, 3)
            .subscribe(onNext)
        subject.onNext(5)

        verifyOrder {
            onNext(1)
            onNext(2)
            onNext(3)
            onNext(4)
            onNext(5)
        }
    }
}