package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.Observables
import com.tealium.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class CallbackObservableTests {

    lateinit var executorService: ScheduledExecutorService

    @Before
    fun setUp() {
        executorService = Executors.newSingleThreadScheduledExecutor()
    }

    @After
    fun tearDown() {
        executorService.shutdown()
    }

    @Test
    fun callbackObservable_ExecutesImmediately_WhenNotAsync() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.callback<Int> { observer ->
            observer.onNext(1)
        }.subscribe(onNext)

        verify {
            onNext(1)
        }
    }

    @Test
    fun callbackObservable_ExecutesImmediately_WhenNotAsync_FromSubjectEmissions() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)
        val subject = Observables.publishSubject<Int>()

        subject.callback { value, observer ->
            observer.onNext(value)
        }.subscribe(onNext)

        subject.onNext(1)

        verify {
            onNext(1)
        }
    }

    @Test
    fun callbackObservable_ExecutesAsynchronously_FromSubjectEmissions() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)
        val subject = Observables.publishSubject<Int>()

        subject.callback { value, observer ->
            executorService.schedule({
                observer.onNext(value * 2)
            }, 10, TimeUnit.MILLISECONDS)
        }.subscribe(onNext)

        subject.onNext(1)

        verify(timeout = 1000) {
            onNext(2)
        }
    }

    @Test
    fun callbackObservable_Cancels_WhenAsynchronouslyEmitted() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val disposable = Observables.callback<Int> { observer ->
            executorService.schedule({
                observer.onNext( 1)
            }, 10, TimeUnit.MILLISECONDS)
        }.subscribe(onNext)

        disposable.dispose()

        verify(timeout = 1000, inverse = true) {
            onNext(1)
        }
    }

    @Test
    fun callbackObservable_Cancels_WhenAsynchronouslyEmitted_FromSubject() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)
        val subject = Observables.publishSubject<Int>()

        val disposable = subject.callback { value, observer ->
            executorService.schedule({
                observer.onNext( value)
            }, 10, TimeUnit.MILLISECONDS)
        }.subscribe(onNext)

        disposable.dispose()

        subject.assertNoSubscribers()
        verify(timeout = 1000, inverse = true) {
            onNext(1)
        }
    }
}