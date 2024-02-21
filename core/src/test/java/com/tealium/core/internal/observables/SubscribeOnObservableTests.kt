package com.tealium.core.internal.observables

import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.observables.ObservableUtils.assertNoSubscribers
import com.tealium.core.internal.observables.ObservableUtils.getMockObserver
import com.tealium.core.internal.observables.ObservableUtils.getSubject
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SubscribeOnObservableTests {
    lateinit var subscribeThread: Thread
    lateinit var subscribeExecutorService: ExecutorService

    @Before
    fun setUp() {
        subscribeExecutorService = Executors.newSingleThreadExecutor {
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
        val subscribeHandler: (Observer<Int>) -> Unit = spyk({
            Assert.assertEquals(subscribeThread, Thread.currentThread())
        })
        val subject = getSubject(
            doSubscribeHandler = subscribeHandler
        )

        val observer = getMockObserver<Int>()
        subject.subscribeOn(subscribeExecutorService)
            .subscribe(observer)

        verify(exactly = 1, timeout = 1000) {
            subscribeHandler.invoke(any())
        }
    }

    @Test
    fun subscribeOn_EmitsValues_OnCallerThread() {
        val testThread = Thread.currentThread()
        val observer = getMockObserver<Int>(onNextHandler = {
            Assert.assertEquals(testThread, Thread.currentThread())
        })

        val subject = Observables.publishSubject<Int>()
        subject.subscribeOn(subscribeExecutorService)
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
        val disposable = subject.subscribeOn(subscribeExecutorService)
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