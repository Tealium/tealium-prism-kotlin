package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IterableCombineObservableTests {

    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        observer = mockk(relaxed = true)
    }

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

        Observables.combine(listOf(subject1, subject2), multiplyAll)
            .subscribe(observer)

        subject1.onNext(5)
        verify(inverse = true) {
            observer.onNext(any())
        }

        subject2.onNext(1)

        verify {
            observer.onNext(5)
        }
    }

    @Test
    fun combine_Emits_All_Subsequent_When_Both_Have_Emitted() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()

        Observables.combine(listOf(subject1, subject2), multiplyAll)
            .subscribe(observer)

        subject1.onNext(5)

        subject2.onNext(1)
        subject2.onNext(2)
        subject2.onNext(3)

        verifyOrder {
            observer.onNext(5)
            observer.onNext(10)
            observer.onNext(15)
        }
    }

    @Test
    fun combine_Does_Not_Emit_When_Only_One_Has_Emitted() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()

        Observables.combine(listOf(subject1, subject2), multiplyAll)
            .subscribe(observer)

        subject1.onNext(5)
        verify(inverse = true) {
            observer.onNext(any())
        }
    }

    @Test
    fun combine_Can_Combine_Multiple_Observables() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val subject3 = Observables.publishSubject<Int>()

        Observables.combine(listOf(subject1, subject2, subject3), multiplyAll)
            .subscribe(observer)

        subject1.onNext(2)
        subject2.onNext(2)
        subject3.onNext(1)
        subject3.onNext(2)
        subject3.onNext(3)

        verify() {
            observer.onNext(4)
            observer.onNext(8)
            observer.onNext(12)
        }
    }

    @Test
    fun combine_Can_Combine_Multiple_Observables_With_Varargs() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val subject3 = Observables.publishSubject<Int>()

        Observables.combine(subject1, subject2, subject3, combiner = multiplyAll)
            .subscribe(observer)

        subject1.onNext(2)
        subject2.onNext(2)
        subject3.onNext(1)
        subject3.onNext(2)
        subject3.onNext(3)

        verify() {
            observer.onNext(4)
            observer.onNext(8)
            observer.onNext(12)
        }
    }

    @Test
    fun combine_Emits_Iterable_In_Correct_Order() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()
        val subject3 = Observables.publishSubject<Int>()

        val combiner: (Iterable<Int>) -> Int = mockk()
        every { combiner.invoke(any()) } answers {
            multiplyAll(this.arg(0))
        }

        Observables.combine(listOf(subject1, subject2, subject3), combiner)
            .subscribe(observer)

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
        val combiner: (Iterable<Int>) -> Int = mockk()
        every { combiner.invoke(any()) } answers {
            multiplyAll(this.arg(0))
        }

        Observables.combine(listOf(), combiner)
            .subscribe(observer)

        verify {
            combiner.invoke(match { it.count() == 0 })
            observer.onNext(0)
        }
    }

    @Test
    fun combine_Can_Emit_Null_Emissions() {
        val nulls = Observables.just<Int?>(null)
        val moreNulls = Observables.just<Int?>(null)
        val observer = mockk<Observer<Iterable<Int?>>>(relaxed = true)

        Observables.combine(listOf(nulls, moreNulls)) {
            it
        }.subscribe(observer)

        verify {
            observer.onNext(listOf(null, null))
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

    @Test
    fun combine_Completes_When_All_Sources_Have_Completed() {
        val source1 = Observables.publishSubject<Int>()
        val source2 = Observables.publishSubject<Int>()
        Observables.combine(listOf(source1, source2)) { _ -> 1 }
            .subscribe(observer)

        source1.onComplete()
        source2.onComplete()

        verify {
            observer.onComplete()
        }
    }

    @Test
    fun combine_Does_Not_Complete_When_One_Sources_Has_Not_Completed_But_Has_Emitted() {
        val source1 = Observables.publishSubject<Int>()
        val source2 = Observables.publishSubject<Int>()
        Observables.combine(listOf(source1, source2)) { _ -> 1 }
            .subscribe(observer)

        source1.onNext(1)
        source1.onComplete()

        verify(inverse = true) {
            observer.onComplete()
        }
    }

    @Test
    fun combine_Completes_When_One_Source_Has_Completed_And_Did_Not_Emit() {
        val source1 = Observables.empty<Int>()
        val source2 = Observables.publishSubject<Int>()
        Observables.combine(listOf(source1, source2)) { _ -> 1 }
            .subscribe(observer)

        source2.onNext(1)
        source2.onComplete()

        verify(exactly = 1) {
            observer.onComplete()
        }
    }

    @Test
    fun combine_Completes_Only_Once_When_Multiple_Sources_Have_Completed_And_Did_Not_Emit() {
        val source1 = Observables.empty<Int>()
        val source2 = Observables.empty<Int>()
        Observables.combine(listOf(source1, source2)) { _ -> 1 }
            .subscribe(observer)

        verify(exactly = 1) {
            observer.onComplete()
        }
    }

    @Test
    fun combine_Does_Not_Emit_Anymore_Downstream_After_Completion() {
        val source1 = Observables.publishSubject<Int>()
        val source2 = Observables.publishSubject<Int>()
        Observables.combine(listOf(source1, source2)) { _ -> 1 }
            .subscribe(observer)

        source1.onComplete()
        source2.onComplete()

        source1.onNext(1)
        source2.onNext(2)

        verify(inverse = true) {
            observer.onNext(any())
        }
    }

    @Test
    fun combine_Disposable_Is_Disposed_After_OnComplete() {
        val source1 = Observables.publishSubject<Int>()
        val source2 = Observables.publishSubject<Int>()

        val disposable = Observables.combine(listOf(source1, source2)) { _ -> 1 }
            .subscribe(observer)

        source1.onComplete()
        source2.onComplete()

        assertTrue(disposable.isDisposed)
    }

    @Test
    fun combine_Disposable_Is_Not_Disposed_When_Only_One_Source_Is_Complete_But_Has_Emitted() {
        val source1 = Observables.publishSubject<Int>()
        val source2 = Observables.publishSubject<Int>()

        val disposable = Observables.combine(listOf(source1, source2)) { _ -> 1 }
            .subscribe(observer)

        source1.onNext(1)
        source1.onComplete()

        assertFalse(disposable.isDisposed)
    }

    @Test
    fun combine_Disposable_Is_Disposed_When_Only_One_Source_Is_Complete_And_Has_Not_Emitted() {
        val source1 = Observables.publishSubject<Int>()
        val source2 = Observables.publishSubject<Int>()

        val disposable = Observables.combine(listOf(source1, source2)) { _ -> 1 }
            .subscribe(observer)

        source1.onComplete()

        assertTrue(disposable.isDisposed)
    }
}