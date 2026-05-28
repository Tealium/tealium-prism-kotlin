package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ResubscribingObservableTests {

    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        observer = mockk(relaxed = true)
    }

    @Test
    fun resubscribingWhile_Resubscribes_Until_Condition_Is_Not_Met() {
        val source = Observables.just(1, 2, 3, 4, 5)
        var count = 0
        every { observer.onNext(any()) } answers { count++ }

        source.resubscribingWhile { count < 5 }
            .subscribe(observer)

        verify(exactly = 5) {
            observer.onNext(1)
        }
    }

    @Test
    fun resubscribingWhile_Completes_Once_Condition_Is_Not_Met() {
        val source = Observables.just(1)
        var shouldContinue = true
        every { observer.onNext(any()) } answers { shouldContinue = false }

        source.resubscribingWhile { shouldContinue }
            .subscribe(observer)

        verify(exactly = 1) {
            observer.onComplete()
        }
    }

    @Test
    fun resubscribingWhile_Completes_When_Source_Completes() {
        val source = Observables.publishSubject<Int>()

        source.resubscribingWhile { true }
            .subscribe(observer)

        source.onNext(1)
        source.onComplete()

        verify(exactly = 1) {
            observer.onNext(1)
            observer.onComplete()
        }
    }

    @Test
    fun resubscribingWhile_Completes_When_No_Initial_Emission_But_Source_Completes() {
        val source = Observables.empty<Int>()

        source.resubscribingWhile { true }
            .subscribe(observer)

        verify(inverse = true) {
            observer.onNext(any())
        }
        verify(exactly = 1) {
            observer.onComplete()
        }
    }

    @Test
    fun resubscribingWhile_Takes_One_And_Disposes_On_Each_Emission() {
        var subscribeCount = 0
        var disposeCount = 0
        Observables.create { observer ->
            if (subscribeCount < 3) {
                subscribeCount++
                observer.onNext(subscribeCount)
            }
            Subscription { disposeCount++ }
        }.resubscribingWhile { subscribeCount < 3 }
            .subscribe(observer)

        assertEquals(3, subscribeCount)
        assertEquals(3, disposeCount)
        verify {
            observer.onNext(1)
            observer.onNext(2)
            observer.onNext(3)
        }
    }

    @Test
    fun resubscribingWhile_Does_Not_Emit_If_Disposed() {
        var innerObserver: Observer<Int>? = null
        val sub = Observables.create { observer ->
            innerObserver = observer
            Subscription()
        }.resubscribingWhile { true }
            .subscribe(observer)

        sub.dispose()
        innerObserver!!.onNext(1)

        verify(inverse = true) {
            observer.onNext(any())
        }
    }

    @Test
    fun resubscribingWhile_Does_Not_Emit_After_Completion() {
        val source = Observables.publishSubject<Int>()

        source.resubscribingWhile { true }
            .subscribe(observer)

        source.onNext(1)
        source.onComplete()
        source.onNext(2)

        verify(exactly = 1) {
            observer.onNext(1)
            observer.onComplete()
        }
        verify(inverse = true) {
            observer.onNext(2)
        }
    }
}