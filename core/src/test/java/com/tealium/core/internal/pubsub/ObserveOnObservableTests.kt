package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.internal.misc.TealiumScheduler
import com.tealium.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.core.internal.pubsub.ObservableUtils.getMockObserver
import com.tealium.core.internal.pubsub.ObservableUtils.getSubject
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class ObserveOnObservableTests {
    lateinit var observeThread: Thread
    lateinit var observerExecutorService: ScheduledExecutorService

    @Before
    fun setUp() {
        observerExecutorService = Executors.newSingleThreadScheduledExecutor {
            observeThread = Thread(it, "observeThread")
            observeThread
        }
    }

    @After
    fun tearDown() {
        observerExecutorService.shutdown()
    }

    @Test
    fun observeOn_SubscribesOn_CallingThread() {
        val testThread = Thread.currentThread()
        val assertion: (Observer<Int>) -> Unit = mockk(relaxed = true)
        val subscribeHandler: (Observer<Int>) -> Unit = {
            assertEquals(testThread, Thread.currentThread())
            assertion(it)
        }
        val subject = getSubject(
            doSubscribeHandler = subscribeHandler
        )

        val observer = getMockObserver<Int>()
        subject.observeOn(testTealiumScheduler)
            .subscribe(observer)

        verify(exactly = 1, timeout = 1000) {
            assertion(any())
        }
    }

    @Test
    fun observeOn_EmitsValues_OnProvidedThread() {
        val scheduler = TealiumScheduler(observerExecutorService)
        val observer = getMockObserver<Int>(onNextHandler = {
            assertEquals(observeThread, Thread.currentThread())
        })

        val subject = Observables.publishSubject<Int>()
        subject.observeOn(testTealiumScheduler)
            .subscribe(observer)

        subject.onNext(1)
        subject.onNext(1)
        subject.onNext(1)
        subject.onNext(1)

        verify(exactly = 4, timeout = 1000) {
            observer.onNext(1)
        }
    }

    @Test
    fun observeOn_DoesNot_EmitValues_WhenSubscriptionDisposed() {
        val observer = getMockObserver<Int>()
        val subject = Observables.publishSubject<Int>()
        val disposable = subject.observeOn(testTealiumScheduler)
            .subscribe(observer)

        disposable.dispose()
        subject.onNext(1)

        subject.assertNoSubscribers()
        verify(inverse = true, timeout = 1000) {
            observer.onNext(1)
        }
    }
}