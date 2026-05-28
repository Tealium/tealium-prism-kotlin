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

class FlatMapObservableTests {

    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        observer = mockk(relaxed = true)
    }

    @Test
    fun flatMap_Transforms_Emissions_From_All_Observables() {
        Observables.just(1, 2, 3)
            .flatMap {
                Observables.just(it, it + 1)
            }
            .subscribe(observer)

        verifyOrder {
            observer.onNext(1)
            observer.onNext(2)

            observer.onNext(2)
            observer.onNext(3)

            observer.onNext(3)
            observer.onNext(4)
        }
    }

    @Test
    fun flatMap_Transforms_Emissions_From_All_Observables_In_Order() {
        val subject = Observables.publishSubject<Int>()

        subject.flatMap {
            Observables.just(it, it + 1)
        }.subscribe(observer)

        subject.onNext(1)
        subject.onNext(2)
        subject.onNext(3)

        verify {
            observer.onNext(1)
            observer.onNext(2)

            observer.onNext(2)
            observer.onNext(3)

            observer.onNext(3)
            observer.onNext(4)
        }
    }

    @Test
    fun flatMap_Dispose_Stops_Emitting() {
        val subject = Observables.publishSubject<Int>()

        val subscription = subject.flatMap {
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
    fun flatMap_Completes_When_Both_Source_And_All_Inner_Observables_Have_Completed() {
        Observables.just(1)
            .flatMap {
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
    fun flatMap_Does_Not_Complete_When_Source_Has_Not_Completed_But_All_Inner_Observables_Have_Completed() {
        val source = Observables.publishSubject<Int>()
        source.flatMap {
            Observables.just(it)
        }.subscribe(observer)

        verify(inverse = true) {
            observer.onComplete()
        }
    }

    @Test
    fun flatMap_Does_Not_Complete_When_Source_Has_Completed_But_Not_All_Inner_Observables_Have_Completed() {
        val source = Observables.publishSubject<Int>()
        val inner1 = Observables.publishSubject<Int>()
        val inner2 = Observables.publishSubject<Int>()
        source.flatMap {
            if (it == 1) {
                inner1
            } else {
                inner2
            }
        }.subscribe(observer)

        source.onNext(1) // subscribe inner1
        source.onNext(2) // subscribe inner2
        source.onComplete()

        verify(inverse = true) {
            observer.onComplete()
        }
    }

    @Test
    fun flatMap_Completes_When_Source_Completes_Without_Any_Emissions() {
        val source = Observables.empty<Int>()

        source.flatMap {
            Observables.just(1)
        }.subscribe(observer)

        verify(exactly = 1) {
            observer.onComplete()
        }
    }

    @Test
    fun flatMap_Disposable_Is_Disposed_After_OnComplete() {
        val source = Observables.publishSubject<Int>()
        val inner = Observables.publishSubject<Int>()

        val disposable = source.flatMap { inner }
            .subscribe(observer)
        source.onNext(1) // subscribe inner
        source.onComplete()
        inner.onComplete()

        assertTrue(disposable.isDisposed)
    }

    @Test
    fun flatMap_Disposable_Is_Not_Disposed_When_Source_Is_Not_Complete() {
        val source = Observables.publishSubject<Int>()

        val disposable = source.flatMap { Observables.just(1) }
            .subscribe(observer)
        source.onNext(1) // subscribe inner

        assertFalse(disposable.isDisposed)
    }

    @Test
    fun flatMap_Disposable_Is_Not_Disposed_When_Inner_Is_Not_Complete() {
        val source = Observables.publishSubject<Int>()
        val inner = Observables.publishSubject<Int>()

        val disposable = source.flatMap { inner }
            .subscribe(observer)
        source.onNext(1) // subscribe inner
        source.onComplete()

        assertFalse(disposable.isDisposed)
    }
}