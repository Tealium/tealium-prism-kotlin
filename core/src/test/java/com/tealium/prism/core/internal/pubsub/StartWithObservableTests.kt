package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StartWithObservableTests {

    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        observer = mockk(relaxed = true)
    }

    @Test
    fun startWith_Emits_Given_Items_Before_Next_One() {
        val subject = Observables.publishSubject<Int>()

        subject.startWith(1, 2, 3)
            .subscribe(observer)
        subject.onNext(4)

        verifyOrder {
            observer.onNext(1)
            observer.onNext(2)
            observer.onNext(3)
            observer.onNext(4)
        }
    }

    @Test
    fun startWith_Emits_Given_Items_Before_Subjects_State() {
        val subject = Observables.stateSubject<Int>(4)

        subject.startWith(1, 2, 3)
            .subscribe(observer)
        subject.onNext(5)

        verifyOrder {
            observer.onNext(1)
            observer.onNext(2)
            observer.onNext(3)
            observer.onNext(4)
            observer.onNext(5)
        }
    }

    @Test
    fun startWith_Emits_OnComplete_When_Source_Completes() {
        val subject = Observables.publishSubject<Int>()
        subject.startWith(1)
            .subscribe(observer)

        subject.onComplete()

        verify {
            observer.onNext(1)
            observer.onComplete()
        }
    }

    @Test
    fun startWith_Disposable_Is_Disposed_After_OnComplete() {
        val subject = Observables.publishSubject<Int>()
        val disposable = subject.startWith(1)
            .subscribe(observer)

        assertFalse(disposable.isDisposed)

        subject.onComplete()
        assertTrue(disposable.isDisposed)
    }
}