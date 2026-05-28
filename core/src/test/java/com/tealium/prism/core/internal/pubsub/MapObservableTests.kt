package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MapObservableTests {

    private lateinit var observer: Observer<Long>

    @Before
    fun setUp() {
        observer = mockk(relaxed = true)
    }

    @Test
    fun map_Transforms_Emissions() {
        val subject = Observables.publishSubject<Int>()

        subject.map(Int::toLong)
            .subscribe(observer)

        subject.onNext(1)
        verify {
            observer.onNext(1L)
        }
    }

    @Test
    fun map_Transforms_Emissions_And_Can_Be_Chained() {
        val subject = Observables.publishSubject<Int>()

        subject.map(Int::toLong)
            .map(Long::inc) // 6
            .map(Long::inc) // 7
            .subscribe(observer)

        subject.onNext(1)

        verify {
            observer.onNext(3L)
        }
    }

    @Test
    fun map_Dispose_Stops_Emitting() {
        val subject = Observables.publishSubject<Long>()

        val subscription = subject.map(Long::inc)
            .subscribe(observer)

        subject.onNext(1)

        subscription.dispose()
        subject.onNext(2)

        subject.assertNoSubscribers()
        verify {
            observer.onNext(2)
        }
        verify(inverse = true) {
            observer.onNext(3)
        }
    }

    @Test
    fun map_Emits_OnComplete_When_Source_Completes() {
        val subject = Observables.publishSubject<Long>()
        subject.map { it }
            .subscribe(observer)

        subject.onNext(1)
        subject.onComplete()

        verify {
            observer.onNext(1)
            observer.onComplete()
        }
    }

    @Test
    fun map_Disposable_Is_Disposed_After_OnComplete() {
        val subject = Observables.publishSubject<Long>()
        val disposable = subject.map { it }
            .subscribe(observer)

        assertFalse(disposable.isDisposed)

        subject.onComplete()
        assertTrue(disposable.isDisposed)
    }
}