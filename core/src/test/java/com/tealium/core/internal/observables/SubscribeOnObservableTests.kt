package com.tealium.core.internal.observables

import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.TealiumScheduler
import com.tealium.core.internal.observables.ObservableUtils.assertNoSubscribers
import com.tealium.core.internal.observables.ObservableUtils.getMockObserver
import com.tealium.core.internal.observables.ObservableUtils.getSubject
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class SubscribeOnObservableTests {
    lateinit var subscribeThread: Thread
    lateinit var subscribeExecutorService: ScheduledExecutorService

    @Before
    fun setUp() {
        subscribeExecutorService = Executors.newSingleThreadScheduledExecutor() {
            subscribeThread = Thread(it, "subscribeThread")
            subscribeThread
        }
    }

    @After
    fun tearDown() {
        subscribeExecutorService.shutdown()
    }

    @Test
    fun subscribeOn_Subscribes_OnProvidedThread() {
        val scheduler = TealiumScheduler(subscribeExecutorService)

        val assertion: (Observer<Int>) -> Unit = mockk(relaxed = true)
        val subscribeHandler: (Observer<Int>) -> Unit = {
            Assert.assertEquals(subscribeThread, Thread.currentThread())
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
        val disposable = subject.subscribeOn(TealiumScheduler(subscribeExecutorService))
            .subscribe(observer)


        disposable.dispose()
        subscribeThread.join(10)

        subject.onNext(1)

        subject.assertNoSubscribers()
        verify(inverse = true, timeout = 1000) {
            observer.onNext(1)
        }
    }
}