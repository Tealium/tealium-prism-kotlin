package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.Observables
import com.tealium.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class IterableCombineObservableTests {

    private val multiplyAll: (Iterable<Int>) -> Int = {
        if (it.count() > 0) {
            it.reduce { acc, i -> acc * i }
        } else {
            0
        }
    }

    @Test
    fun combine_Emits_Only_When_Both_Have_Emitted() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.combine(listOf(subject1, subject2), multiplyAll)
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
    fun combine_Emits_All_Subsequent_When_Both_Have_Emitted() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.combine(listOf(subject1, subject2), multiplyAll)
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
    fun combine_Does_Not_Emit_When_Only_One_Has_Emitted() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.combine(listOf(subject1, subject2), multiplyAll)
            .subscribe(onNext)

        subject1.onNext(5)
        verify(inverse = true) {
            onNext(any())
        }
    }

    @Test
    fun combine_Can_Combine_Multiple_Observables() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val subject3 = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.combine(listOf(subject1, subject2, subject3), multiplyAll)
            .subscribe(onNext)

        subject1.onNext(2)
        subject2.onNext(2)
        subject3.onNext(1)
        subject3.onNext(2)
        subject3.onNext(3)

        verify() {
            onNext(4)
            onNext(8)
            onNext(12)
        }
    }

    @Test
    fun combine_Can_Combine_Multiple_Observables_With_Varargs() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val subject3 = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.combine(subject1, subject2, subject3, combiner = multiplyAll)
            .subscribe(onNext)

        subject1.onNext(2)
        subject2.onNext(2)
        subject3.onNext(1)
        subject3.onNext(2)
        subject3.onNext(3)

        verify() {
            onNext(4)
            onNext(8)
            onNext(12)
        }
    }

    @Test
    fun combine_Emits_Iterable_In_Correct_Order() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val subject3 = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val combiner: (Iterable<Int>) -> Int = mockk()
        every { combiner.invoke(any()) } answers {
            multiplyAll(this.arg(0))
        }

        Observables.combine(listOf(subject1, subject2, subject3), combiner)
            .subscribe(onNext)

        subject1.onNext(1)
        subject2.onNext(2)
        subject3.onNext(3)

        verify {
            combiner.invoke(match {
                it.elementAt(0) == 1
                        && it.elementAt(1) == 2
                        && it.elementAt(2) == 3
            })
        }
    }

    @Test
    fun combine_Emits_Immediately_When_Sources_Empty() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val combiner: (Iterable<Int>) -> Int = mockk()
        every { combiner.invoke(any()) } answers {
            multiplyAll(this.arg(0))
        }

        Observables.combine(listOf(), combiner)
            .subscribe(onNext)

        verify {
            combiner.invoke(match { it.count() == 0 })
            onNext(0)
        }
    }

    @Test
    fun combine_Can_Emit_Null_Emissions() {
        val nulls = Observables.just<Int?>(null)
        val moreNulls = Observables.just<Int?>(null)
        val onNext = mockk<(Iterable<Int?>) -> Unit>(relaxed = true)

        Observables.combine(listOf(nulls, moreNulls)) {
            it
        }.subscribe(onNext)

        verify {
            onNext(listOf(null, null))
        }
    }

    @Test
    fun combine_Dispose_Stops_Emitting() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val subject3 = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = Observables.combine(listOf(subject1, subject2, subject3), multiplyAll)
            .subscribe(onNext)

        subject1.onNext(1)
        subject2.onNext(1)
        subject3.onNext(1)

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