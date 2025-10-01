package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.misc.SingleThreadedScheduler
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.prism.core.internal.pubsub.ObservableUtils.getMockObserver
import com.tealium.prism.core.internal.pubsub.ObservableUtils.getSubject
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
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
    fun subscribeOn_Subscribes_OnProvidedThread() {
        val scheduler = SingleThreadedScheduler(subscribeThreadFactory)

        val assertion: (Observer<Int>) -> Unit = mockk(relaxed = true)
        val subscribeHandler: (Observer<Int>) -> Unit = {
            Assert.assertEquals(subscribeThreadFactory.thread, Thread.currentThread())
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
    fun subscribeOn_EmitsValues_OnCallerThread() {
        val testThread = Thread.currentThread()
        val observer = getMockObserver<Int>(onNextHandler = {
            Assert.assertEquals(testThread, Thread.currentThread())
        })

        val subject = Observables.publishSubject<Int>()
        subject.subscribeOn(testTealiumScheduler)
            .subscribe(observer)

        Thread.sleep(100)

        subject.onNext(1)
        subject.onNext(1)
        subject.onNext(1)
        subject.onNext(1)

        verify(exactly = 4, timeout = 1000) {
            observer.onNext(1)
        }
    }

    @Test
    fun subscribeOn_DoesNot_EmitValues_WhenSubscriptionDisposed() {
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
}