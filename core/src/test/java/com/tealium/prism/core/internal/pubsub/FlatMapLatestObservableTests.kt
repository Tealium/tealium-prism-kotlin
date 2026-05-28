package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertSubscriberCount
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FlatMapLatestObservableTests {

    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        observer = mockk(relaxed = true)
    }

    @Test
    fun flatMapLatest_Emits_Only_Latest_From_Latest_Inner_Observable() {
        val inner1 = Observables.publishSubject<Int>()
        val inner2 = Observables.publishSubject<Int>()
        Observables.just(1, 2)
            .flatMapLatest {
                if (it <= 1) {
                    inner1
                } else {
                    inner2
                }
            }.subscribe(observer)

        inner1.onNext(100)
        inner2.onNext(200)

        verify(inverse = true) {
            observer.onNext(100)
        }
        verify {
            observer.onNext(200)
        }
    }

    @Test
    fun flatMapLatest_Dispose_StopsEmitting() {
        val subject = Observables.publishSubject<Int>()

        val subscription = subject.flatMapLatest {
            Observables.just(it, it + 1)
        }.subscribe(observer)

        subject.onNext(1)
        subscription.dispose()
        subject.onNext(3)

        subject.assertNoSubscribers()
        verify {
            observer.onNext(1)
            observer.onNext(2)
        }
        verify(inverse = true) {
            observer.onNext(3)
            observer.onNext(4)
        }
    }

    @Test
    fun flatMapLatest_Disposes_Previous_Subscriptions() {
        val subject = Observables.publishSubject<Boolean>()
        val trues = Observables.publishSubject<Boolean>()
        val falses = Observables.publishSubject<Boolean>()
        val observer = mockk<Observer<Boolean>>(relaxed = true)

        val subscription = subject.flatMapLatest { isTrue ->
            if (isTrue) trues else falses
        }.subscribe(observer)

        subject.onNext(true)
        trues.assertSubscriberCount(1)
        falses.assertSubscriberCount(0)

        subject.onNext(false)
        trues.assertSubscriberCount(0)
        falses.assertSubscriberCount(1)

        trues.onNext(true) // dropped
        falses.onNext(false)

        subscription.dispose()
        subject.assertNoSubscribers()
        trues.assertNoSubscribers()
        falses.assertNoSubscribers()

        verify {
            observer.onNext(false)
        }
        verify(inverse = true) {
            observer.onNext(true)
        }
    }

    @Test
    fun flatMapLatest_Conflates_Reentrant_Emissions_When_Multiple_Occur_During_Transform() {
        val subject = Observables.publishSubject<Int>()

        val onNext = mockk<(Int) -> Unit>(relaxed = true)
        var transformCount = 0
        subject.flatMapLatest<Int> { value ->
            transformCount++
            if (value == 1) {
                subject.onNext(2)
                subject.onNext(3)
                subject.onNext(4)
                Observables.empty()
            } else {
                Observables.empty()
            }
        }.subscribe(onNext)

        subject.onNext(1)

        assertEquals(2, transformCount)
    }

    @Test
    fun flatMapLatest_Disposes_Previous_Subscriptions_When_Reentrant_Emissions_Occur() {
        val subject = Observables.publishSubject<Int>()
        val innerSubject1 = Observables.publishSubject<Int>()
        val innerSubject2 = Observables.publishSubject<Int>()
        val innerSubject3 = Observables.publishSubject<Int>()

        subject.flatMapLatest { value ->
            if (value == 1) {
                innerSubject1
                    .startWith(2)
                    .forEach { subject.onNext(it) }
            } else if (value == 2) {
                innerSubject1
                    .startWith(3)
                    .forEach { subject.onNext(it) }
            } else {
                innerSubject3
            }
        }.subscribe(observer)

        // Emit first value, which synchronously emits second value
        subject.onNext(1)

        // Emit from all inner subjects
        innerSubject1.onNext(100)
        innerSubject2.onNext(200)
        innerSubject3.onNext(300)

        innerSubject1.assertNoSubscribers()
        innerSubject2.assertNoSubscribers()
        innerSubject3.assertSubscriberCount(1)
        verify(exactly = 0) { observer.onNext(100) }
        verify(exactly = 0) { observer.onNext(200) }
        verify(exactly = 1) {
            observer.onNext(2)
            observer.onNext(3)
            observer.onNext(300)
        } // Only latest should emit
    }

    @Test
    fun flatMapLatest_Emits_Legitimate_Null_Values() {
        val subject = Observables.publishSubject<Int?>()
        val onNext = mockk<(Int?) -> Unit>(relaxed = true)

        subject.flatMapLatest { int ->
            Observables.just(int)
        }.subscribe(onNext)

        subject.onNext(null)

        verify {
            onNext.invoke(null)
        }
    }

    @Test
    fun flatMapLatest_Completes_When_Both_Source_And_Latest_Inner_Observable_Has_Completed() {
        Observables.just(1)
            .flatMapLatest {
                Observables.just(it, it + 1)
            }
            .subscribe(observer)

        verify {
            observer.onNext(1)
            observer.onNext(2)
            observer.onComplete()
        }
    }

    @Test
    fun flatMapLatest_Does_Not_Complete_When_Source_And_Previous_Inner_Have_Completed_But_Latest_Inner_Observable_Has_Not_Completed() {
        val inner1 = Observables.publishSubject<Int>()
        val inner2 = Observables.publishSubject<Int>()
        Observables.just(1, 2)
            .flatMapLatest {
                if (it == 1) inner1 else inner2
            }
            .subscribe(observer)

        inner1.onComplete()
        verify(inverse = true) {
            observer.onComplete()
        }

        inner2.onComplete()
        verify {
            observer.onComplete()
        }

    }

    @Test
    fun flatMapLatest_Does_Not_Complete_When_Source_Has_Not_Completed_But_Inner_Observable_Has_Completed() {
        val source = Observables.publishSubject<Int>()
        source.flatMapLatest {
            Observables.just(it)
        }.subscribe(observer)

        verify(inverse = true) {
            observer.onComplete()
        }
    }

    @Test
    fun flatMapLatest_Does_Not_Complete_When_Source_Has_Completed_But_Latest_Inner_Observable_Has_Not_Completed() {
        val source = Observables.publishSubject<Int>()
        val inner = Observables.publishSubject<Int>()
        source.flatMapLatest {
            if (it == 1) Observables.just(1) else inner
        }.subscribe(observer)

        source.onNext(1) // subscribe just
        source.onNext(2) // subscribe inner
        source.onComplete()

        verify(inverse = true) {
            observer.onComplete()
        }
    }

    @Test
    fun flatMapLatest_Completes_When_Source_Completes_Without_Any_Emissions() {
        val source = Observables.empty<Int>()

        source.flatMapLatest {
            Observables.just(1)
        }.subscribe(observer)

        verify(exactly = 1) {
            observer.onComplete()
        }
    }

    @Test
    fun flatMapLatest_Disposable_Is_Disposed_After_OnComplete() {
        val source = Observables.publishSubject<Int>()
        val inner = Observables.publishSubject<Int>()

        val disposable = source.flatMapLatest { inner }
            .subscribe(observer)
        source.onNext(1) // subscribe inner
        source.onComplete()
        inner.onComplete()

        assertTrue(disposable.isDisposed)
    }

    @Test
    fun flatMapLatest_Disposable_Is_Not_Disposed_When_Source_Is_Not_Complete() {
        val source = Observables.publishSubject<Int>()

        val disposable = source.flatMapLatest { Observables.just(1) }
            .subscribe(observer)
        source.onNext(1) // subscribe inner

        assertFalse(disposable.isDisposed)
    }

    @Test
    fun flatMapLatest_Disposable_Is_Not_Disposed_When_Inner_Is_Not_Complete() {
        val source = Observables.publishSubject<Int>()
        val inner = Observables.publishSubject<Int>()

        val disposable = source.flatMapLatest { inner }
            .subscribe(observer)
        source.onNext(1) // subscribe inner
        source.onComplete()

        assertFalse(disposable.isDisposed)
    }
}