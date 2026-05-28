package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.misc.SingleThreadedScheduler
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.prism.core.internal.pubsub.ObservableUtils.getMockObserver
import com.tealium.prism.core.internal.pubsub.ObservableUtils.getSubject
import com.tealium.tests.common.assertWithTimeout
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SubscribeOnObservableTests {
    lateinit var subscribeThreadFactory: SingleThreadedScheduler.SingleThreadFactory

    @Before
    fun setUp() {
        subscribeThreadFactory = SingleThreadedScheduler.SingleThreadFactory("tealium-test")
    }

//    @After
//    fun tearDown() {
//        subscribeExecutorService.shutdown()
//    }

    @Test
    fun subscribeOn_Subscribes_On_Provided_Thread() {
        val scheduler = SingleThreadedScheduler(subscribeThreadFactory)

        val assertion: (Observer<Int>) -> Unit = mockk(relaxed = true)
        val subscribeHandler: (Observer<Int>) -> Unit = {
            assertEquals(subscribeThreadFactory.thread, Thread.currentThread())
            assertion(it)
        }
        val subject = getSubject(
            doSubscribeHandler = subscribeHandler
        )

        val observer = getMockObserver<Int>()
        subject.subscribeOn(scheduler)
            .subscribe(observer)

        verify(exactly = 1, timeout = 1000) {
            assertion(any())
        }
    }

    @Test
    fun subscribeOn_Emits_Values_On_Caller_Thread() {
        val testThread = Thread.currentThread()
        val observer = getMockObserver<Int>(onNextHandler = {
            Assert.assertEquals(testThread, Thread.currentThread())
        })

        val subject = Observables.publishSubject<Int>()
        subject.subscribeOn(testTealiumScheduler)
            .subscribe(observer)

        // await actual subscription before emitting
        assertWithTimeout { subject.count == 1 }

        subject.onNext(1)
        subject.onNext(1)
        subject.onNext(1)
        subject.onNext(1)

        verify(exactly = 4, timeout = 1000) {
            observer.onNext(1)
        }
    }

    @Test
    fun subscribeOn_Does_Not_Emit_Values_When_Subscription_Disposed() {
        val observer = getMockObserver<Int>()
        val subject = Observables.publishSubject<Int>()
        val disposable = subject.subscribeOn(SingleThreadedScheduler(subscribeThreadFactory))
            .subscribe(observer)


        disposable.dispose()
        subscribeThreadFactory.thread!!.join(10)

        subject.onNext(1)

        subject.assertNoSubscribers()
        verify(inverse = true, timeout = 1000) {
            observer.onNext(1)
        }
    }

    @Test
    fun subscribeOn_Completes_When_Source_Completes() {
        val observer = mockk<Observer<Int>>(relaxed = true)
        val subject = Observables.publishSubject<Int>()
        subject.subscribeOn(testTealiumScheduler)
            .subscribe(observer)

        // await actual subscription before emitting
        assertWithTimeout { subject.count == 1 }

        subject.onNext(1)
        subject.onComplete()

        verify {
            observer.onNext(1)
            observer.onComplete()
        }
    }

    @Test
    fun subscribeOn_Emits_onComplete_On_Caller_Thread() {
        val callerThread = Thread.currentThread()
        val scheduler = SingleThreadedScheduler(subscribeThreadFactory)
        val assertion: () -> Unit = mockk(relaxed = true)
        val observer = getMockObserver<Int>(onCompleteHandler = {
            assertEquals(callerThread, Thread.currentThread())
            assertion()
        })

        val subject = Observables.publishSubject<Int>()
        subject.subscribeOn(scheduler)
            .subscribe(observer)

        // await actual subscription before emitting
        assertWithTimeout { subject.count == 1 }

        subject.onNext(1)
        subject.onComplete()

        verify(timeout = 1000) {
            observer.onNext(1)
            observer.onComplete()
            assertion()
        }
    }

    @Test
    fun subscribeOn_Disposable_Is_Disposed_After_OnComplete() {
        val observer = getMockObserver<Int>()
        val subject = Observables.publishSubject<Int>()
        val disposable = subject.subscribeOn(testTealiumScheduler)
            .subscribe(observer)

        subject.onComplete()

        assertWithTimeout { disposable.isDisposed }
    }
}