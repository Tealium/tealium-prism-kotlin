package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Consumer
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class CallbackObservableTests {

    private lateinit var observer: Observer<Int>
    private lateinit var executorService: ScheduledExecutorService

    @Before
    fun setUp() {
        executorService = Executors.newSingleThreadScheduledExecutor()
        observer = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        executorService.shutdown()
    }

    @Test
    fun callback_Observable_Executes_Immediately_When_Not_Async() {
        Observables.callback { onNext ->
            onNext.accept(1)
        }.subscribe(observer)

        verify {
            observer.onNext(1)
        }
    }

    @Test
    fun callback_Observable_Executes_Immediately_When_Not_Async_From_Subject_Emissions() {
        val subject = Observables.publishSubject<Int>()

        subject.callback { value, onNext ->
            onNext.accept(value)
        }.subscribe(observer)

        subject.onNext(1)

        verify {
            observer.onNext(1)
        }
    }

    @Test
    fun callback_Observable_Executes_Asynchronously_From_Subject_Emissions() {
        val subject = Observables.publishSubject<Int>()

        subject.callback { value, onNext ->
            executorService.schedule({
                onNext.accept(value * 2)
            }, 10, TimeUnit.MILLISECONDS)
        }.subscribe(observer)

        subject.onNext(1)

        verify(timeout = 1000) {
            observer.onNext(2)
        }
    }

    @Test
    fun callback_Observable_Cancels_When_Asynchronously_Emitted() {
        val disposable = Observables.callback { onNext ->
            executorService.schedule({
                onNext.accept( 1)
            }, 10, TimeUnit.MILLISECONDS)
        }.subscribe(observer)

        disposable.dispose()

        verify(timeout = 1000, inverse = true) {
            observer.onNext(1)
        }
    }

    @Test
    fun callback_Observable_Cancels_When_Asynchronously_Emitted_From_Subject() {
        val subject = Observables.stateSubject(1)

        val disposable = subject.callback { value, onNext ->
            executorService.schedule({
                onNext.accept( value)
            }, 10, TimeUnit.MILLISECONDS)
        }.subscribe(observer)

        disposable.dispose()

        subject.assertNoSubscribers()
        verify(timeout = 1000, inverse = true) {
            observer.onNext(1)
        }
    }

    @Test
    fun callback_Observable_Completes_After_Emitting_Result() {
        Observables.callback { onNext ->
            onNext.accept(1)
        }.subscribe(observer)

        verify {
            observer.onNext(1)
            observer.onComplete()
        }
    }

    @Test
    fun callback_Intermediate_Observable_Completes_After_Source_Completes_And_Emitting_Result() {
        val subject = Observables.stateSubject(1)

        subject.callback { value, onNext ->
            onNext.accept(value)
        }.subscribe(observer)

        subject.onComplete()

        verify {
            observer.onNext(1)
            observer.onComplete()
        }
    }

    @Test
    fun callback_Observable_Does_Not_Complete_After_Source_Completes_But_Before_Emitting_Result() {
        val subject = Observables.stateSubject(1)

        subject.callback<Int> { _, _ ->
            // no emission
        }.subscribe(observer)

        subject.onComplete()

        verify(inverse = true) {
            observer.onNext(any())
            observer.onComplete()
        }
    }

    @Test
    fun callback_Observable_Does_Not_Emit_Or_Complete_After_Disposed() {
        val subject = Observables.stateSubject(1)
        var innerObserver: Observer<Int>? = null
        val subscription = subject.callback<Int> { _, _ ->
            innerObserver = observer
        }.subscribe(observer)

        subscription.dispose()
        innerObserver?.onNext(1)

        verify(inverse = true) {
            observer.onNext(1)
            observer.onComplete()
        }
    }

    @Test
    fun callback_Intermediate_Disposable_Is_Disposed_After_Source_Completion_And_Emission() {
        val subject = Observables.stateSubject(1)
        val subscription = subject.callback { value, onNext ->
            onNext.accept(value)
        }.subscribe(observer)

        subject.onComplete()

        assertTrue(subscription.isDisposed)
    }

    @Test
    fun callback_Intermediate_Disposable_Is_Not_Disposed_Before_Emission_Completion() {
        val subject = Observables.stateSubject(1)
        val subscription = subject.callback<Int> { _, _ ->
            // no-op
        }.subscribe(observer)

        assertFalse(subscription.isDisposed)
    }

    @Test
    fun callback_Disposable_Is_Disposed_After_Emission() {
        val subscription = Observables.callback { onNext ->
            onNext.accept(1)
        }.subscribe(observer)

        assertTrue(subscription.isDisposed)
    }

    @Test
    fun callback_Disposable_Is_Not_Disposed_Before_Emission_Completion() {
        val subscription = Observables.callback<Int> {
            // no-op: no emission, so observer never completes
        }.subscribe(observer)

        assertFalse(subscription.isDisposed)
    }

    @Test
    fun callback_Additional_OnNext_Is_Not_Emitted() {
        Observables.callback { onNext ->
            onNext.accept(1)
            onNext.accept(2)
        }.subscribe(observer)

        verify(exactly = 1) { observer.onNext(1) }
        verify(inverse = true) { observer.onNext(2) }
    }

    @Test
    fun callback_Additional_OnComplete_Is_Not_Emitted() {
        Observables.callback { onNext ->
            onNext.accept(1)
            onNext.accept(2)
        }.subscribe(observer)

        verify(exactly = 1) { observer.onComplete() }
    }

    @Test
    fun async_Disposes_Returned_Disposable_On_Completion_When_Emitted_Synchronously() {
        val sub = Disposables.subscription()
        Observables.async { onNext ->
            onNext.accept(1)
            sub
        }.subscribe(observer)

        assertTrue(sub.isDisposed)
    }

    @Test
    fun async_Disposes_Returned_Disposable_On_Completion_When_Emitted_Asynchronously() {
        val sub = Disposables.subscription()
        var innerObserver: Consumer<Int>? = null
        Observables.async { onNext ->
            innerObserver = onNext
            sub
        }.subscribe(observer)

        innerObserver!!.accept(1)

        assertTrue(sub.isDisposed)
    }
}
