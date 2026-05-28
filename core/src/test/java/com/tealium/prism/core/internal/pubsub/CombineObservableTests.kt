package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CombineObservableTests {
    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        observer = mockk(relaxed = true)
    }

    @Test
    fun combine_Emits_Only_When_Both_Have_Emitted() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()

        Observables.combine(subject1, subject2, Int::times)
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

        Observables.combine(subject1, subject2, Int::times)
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

        Observables.combine(subject1, subject2, Int::times)
            .subscribe(observer)

        subject1.onNext(5)
        verify(inverse = true) {
            observer.onNext(any())
        }
    }

    @Test
    fun combine_Can_Combine_Multiple_Types() {
        val subject1 = Observables.publishSubject<String>()
        val subject2 = Observables.publishSubject<Int>()
        val observer = mockk<Observer<String>>(relaxed = true)

        Observables.combine(subject1, subject2, String::plus)
            .subscribe(observer)

        subject1.onNext("a")
        subject2.onNext(1)

        subject1.onNext("b")
        subject2.onNext(2)
        verify {
            observer.onNext("a1")
            observer.onNext("b1")
            observer.onNext("b2")
        }
    }

    @Test
    fun combine_Can_Emit_Null_Values() {
        val subject1 = Observables.publishSubject<String?>()
        val subject2 = Observables.publishSubject<String?>()
        val observer = mockk<Observer<String?>>(relaxed = true)

        Observables.combine(subject1, subject2) { s1, _ ->
            s1
        }.subscribe(observer)

        subject1.onNext(null)
        subject2.onNext(null)
        verify {
            observer.onNext(null)
        }
    }

    @Test
    fun combine_Dispose_Stops_Emitting() {
        val subject1 = Observables.publishSubject<Int>()
        val subject2 = Observables.publishSubject<Int>()

        val subscription = Observables.combine(subject1, subject2, Int::times)
            .subscribe(observer)

        subject1.onNext(1)
        subject2.onNext(1)

        subscription.dispose()
        subject2.onNext(2)

        subject1.assertNoSubscribers()
        subject2.assertNoSubscribers()
        verify {
            observer.onNext(1)
        }
        verify(inverse = true) {
            observer.onNext(2)
        }
    }

    @Test
    fun combine_Completes_When_Both_Sources_Have_Completed() {
        val source1 = Observables.publishSubject<Int>()
        val source2 = Observables.publishSubject<Int>()
        Observables.combine(source1, source2) { e1, _ ->
            e1
        }.subscribe(observer)

        source1.onComplete()
        source2.onComplete()

        verify {
            observer.onComplete()
        }
    }

    @Test
    fun combine_Does_Not_Complete_When_Only_One_Sources_Has_Completed_But_Has_Emitted() {
        val source1 = Observables.publishSubject<Int>()
        val source2 = Observables.publishSubject<Int>()
        Observables.combine(source1, source2) { e1, _ ->
            e1
        }.subscribe(observer)

        source1.onNext(1)
        source1.onComplete()

        verify(inverse = true) {
            observer.onComplete()
        }
    }

    @Test
    fun combine_Does_Not_Emit_Anymore_Downstream_After_Completion() {
        val source1 = Observables.publishSubject<Int>()
        val source2 = Observables.publishSubject<Int>()
        Observables.combine(source1, source2) { e1, _ ->
            e1
        }.subscribe(observer)

        source1.onComplete()
        source2.onComplete()

        source1.onNext(1)
        source2.onNext(2)

        verify(inverse = true) {
            observer.onNext(any())
        }
    }

    @Test
    fun combine_Completes_When_Both_Sources_Have_Completed_And_First_Did_Not_Emit() {
        val source1 = Observables.empty<Int>()
        val source2 = Observables.publishSubject<Int>()
        Observables.combine(source1, source2) { e1, _ ->
            e1
        }.subscribe(observer)

        source2.onComplete()

        verify(exactly = 1) {
            observer.onComplete()
        }
    }

    @Test
    fun combine_Completes_When_Both_Sources_Have_Completed_And_Second_Did_Not_Emit() {
        val source1 = Observables.publishSubject<Int>()
        val source2 = Observables.empty<Int>()
        Observables.combine(source1, source2) { e1, _ ->
            e1
        }.subscribe(observer)

        source1.onComplete()

        verify(exactly = 1) {
            observer.onComplete()
        }
    }

    @Test
    fun combine_Completes_When_Both_Sources_Have_Completed_And_Both_Did_Not_Emit() {
        val source1 = Observables.empty<Int>()
        val source2 = Observables.empty<Int>()
        Observables.combine(source1, source2) { e1, _ ->
            e1
        }.subscribe(observer)

        verify(exactly = 1) {
            observer.onComplete()
        }
    }

    @Test
    fun combine_Completes_When_One_Source_Has_Completed_Without_Emission() {
        val source1 = Observables.empty<Int>()
        val source2 = Observables.publishSubject<Int>()
        Observables.combine(source1, source2) { e1, _ ->
            e1
        }.subscribe(observer)

        verify {
            observer.onComplete()
        }
    }

    @Test
    fun combine_Disposable_Is_Disposed_After_OnComplete() {
        val source1 = Observables.publishSubject<Int>()
        val source2 = Observables.publishSubject<Int>()

        val disposable = Observables.combine(source1, source2) { e1, _ -> e1 }
            .subscribe(observer)

        source1.onComplete()
        source2.onComplete()

        assertTrue(disposable.isDisposed)
    }

    @Test
    fun combine_Disposable_Is_Disposed_When_Only_One_Source_Is_Complete_But_Has_Not_Emitted() {
        val source1 = Observables.publishSubject<Int>()
        val source2 = Observables.publishSubject<Int>()

        val disposable = Observables.combine(source1, source2) { e1, _ -> e1 }
            .subscribe(observer)

        source1.onComplete()

        assertTrue(disposable.isDisposed)
    }

    @Test
    fun combine_Disposable_Is_Not_Disposed_When_Only_One_Source_Is_Complete_And_Has_Emitted() {
        val source1 = Observables.publishSubject<Int>()
        val source2 = Observables.publishSubject<Int>()

        val disposable = Observables.combine(source1, source2) { e1, _ -> e1 }
            .subscribe(observer)

        source1.onNext(1)
        source1.onComplete()

        assertFalse(disposable.isDisposed)
    }
}