package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.ReplaySubject
import com.tealium.core.api.pubsub.StateSubject
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class SubjectAsObservableTests {

    @Test
    fun publishSubject_AsObservable_EmitsAllDownstream() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subject = Observables.publishSubject<Int>()

        subject.asObservable()
            .subscribe(onNext)

        verify(inverse = true) {
            onNext(any())

        }

        subject.onNext(1)
        verify {
            onNext(1)
        }
    }

    @Test(expected = ClassCastException::class)
    fun publishSubject_AsObservable_CannotCastBackToSubject() {
        val observable = Observables.publishSubject<Int>()
            .asObservable()
        val subject = observable as PublishSubject
    }

    @Test
    fun stateSubject_AsObservable_EmitsAllDownstream() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subject = Observables.stateSubject<Int>(1)

        subject.asObservable()
            .subscribe(onNext)
        subject.onNext(2)

        verify {
            onNext(1)
            onNext(2)
        }
    }

    @Test(expected = ClassCastException::class)
    fun stateSubject_AsObservable_CannotCastBackToSubject() {
        val observable = Observables.stateSubject<Int>(1)
            .asObservable()
        val subject = observable as StateSubject
    }

    @Test
    fun replaySubject_AsObservable_EmitsAllDownstream() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subject = Observables.replaySubject<Int>(3)
        subject.onNext(1)
        subject.onNext(2)
        subject.onNext(3)

        val observable = subject.asObservable()
        observable.subscribe(onNext)

        subject.onNext(4)

        verify {
            onNext(1)
            onNext(2)
            onNext(3)
            onNext(4)
        }
    }

    @Test(expected = ClassCastException::class)
    fun replaySubject_AsObservable_CannotCastBackToSubject() {
        val observable = Observables.replaySubject<Int>(1)
            .asObservable()
        val subject = observable as ReplaySubject
    }
}
