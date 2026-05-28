package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test

class TakeWhileObservableTests {

    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        observer = mockk(relaxed = true)
    }

    @Test
    fun takeWhile_Emits_Only_Configured_Amount_Then_Completes() {
        Observables.just(1, 2, 3, 2)
            .takeWhile { it <= 2 }
            .subscribe(observer)

        verify(exactly = 1) {
            observer.onNext(1)
            observer.onNext(2)
            observer.onComplete()
        }

        confirmVerified(observer)
    }

    @Test
    fun takeWhile_Emits_Final_Emission_When_Inclusive_Then_Completes() {
        Observables.just(1, 2, 3, 2)
            .takeWhile(inclusive = true) { it <= 2 }
            .subscribe(observer)

        verify(exactly = 1) {
            observer.onNext(1)
            observer.onNext(2)
            observer.onNext(3)
            observer.onComplete()
        }

        confirmVerified(observer)
    }

    @Test
    fun takeWhile_Auto_Disposes_When_Predicate_Fails() {
        val subject = Observables.publishSubject<Int>()

        val subscription = subject.takeWhile { it < 2 }
            .subscribe(observer)

        subject.onNext(1)
        subject.onNext(2) // auto-dispose

        assertTrue(subscription.isDisposed)
        subject.assertNoSubscribers()
    }

    @Test
    fun takeWhile_Dispose_Stops_Emitting() {
        val subject = Observables.publishSubject<Int>()

        val subscription = subject.takeWhile { it <= 2 }
            .subscribe(observer)

        subject.onNext(1)
        subscription.dispose()
        subject.onNext(2)

        subject.assertNoSubscribers()
        verify {
            observer.onNext(1)
        }
        confirmVerified(observer)
    }

    @Test
    fun takeWhile_Stops_Exclusive_Reentrant_Events_And_Completes() {
        val subject = Observables.publishSubject<Int>()
        every { observer.onNext(1) } answers {
            subject.onNext(2) // reentrantly fail predicate
        }
        every { observer.onNext(2) } answers {
            subject.onNext(1) // reentrantly resatisfy predicate
        }

        subject.takeWhile(false) { it < 2 }
            .subscribe(observer)

        subject.onNext(1)

        subject.assertNoSubscribers()
        verify(exactly = 1) {
            observer.onNext(1)
            observer.onComplete()
        }
        confirmVerified(observer)
    }

    @Test
    fun takeWhile_Stops_Inclusive_Reentrant_Events_And_Completes() {
        val subject = Observables.publishSubject<Int>()
        every { observer.onNext(1) } answers {
            subject.onNext(2) // reentrantly fail predicate
        }
        every { observer.onNext(2) } answers {
            subject.onNext(1) // reentrantly resatisfy predicate
        }

        subject.takeWhile(true) { it < 2 }
            .subscribe(observer)

        subject.onNext(1)

        subject.assertNoSubscribers()
        verify(exactly = 1) {
            observer.onNext(1)
            observer.onNext(2)
            observer.onComplete()
        }
        confirmVerified(observer)
    }
}