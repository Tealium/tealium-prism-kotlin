package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.misc.SingleThreadedScheduler
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertSubscriberCount
import com.tealium.prism.core.internal.pubsub.ObservableUtils.getMockObserver
import com.tealium.prism.core.internal.pubsub.ObservableUtils.getSubject
import com.tealium.tests.common.ManualScheduler
import com.tealium.tests.common.assertWithTimeout
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SubscribeOnObservableTests {
    lateinit var subscribeThreadFactory: SingleThreadedScheduler.SingleThreadFactory

    @Before
    fun setUp() {
        subscribeThreadFactory = SingleThreadedScheduler.SingleThreadFactory("tealium-test")
    }

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
            assertEquals(testThread, Thread.currentThread())
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
    fun subscribeOn_Does_Not_Emit_Values_After_Dispose_On_Real_Scheduler() {
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
    fun subscribeOn_Does_Not_Subscribe_To_Source_When_Disposed_Before_Scheduled_Task_Runs() {
        val scheduler = ManualScheduler()
        val observer = getMockObserver<Int>()
        // Assert on source-subscribe count, not final subscriber count: without the guard the source
        // is subscribed (transiently) then torn down, so the final count is 0 either way.
        var sourceSubscribeCount = 0
        val subject = getSubject<Int>(doSubscribeHandler = { sourceSubscribeCount++ })

        // ManualScheduler runs nothing until runAll(), so we can dispose before the scheduled task runs.
        val disposable = subject.subscribeOn(scheduler)
            .subscribe(observer)
        disposable.dispose()
        scheduler.runAll()

        assertEquals(0, sourceSubscribeCount)
        subject.assertNoSubscribers()
    }

    @Test
    fun subscribeOn_Does_Not_Emit_Remaining_Replay_Values_To_Consumer_Disposed_During_Replay() {
        val scheduler = ManualScheduler()
        val subject = Observables.replaySubject<Int>(2)
        subject.onNext(1)
        subject.onNext(2)

        // replay happens synchronously inside subscribe(), before there's an upstream link to dispose
        lateinit var disposable: Disposable
        val observer = getMockObserver<Int>(onNextHandler = { disposable.dispose() })

        disposable = subject.asObservable().subscribeOn(scheduler)
            .subscribe(observer)
        scheduler.runAll()

        verify(exactly = 1) {
            observer.onNext(1)
        }
        verify(inverse = true) {
            observer.onNext(2)
        }
    }

    @Test
    fun subscribeOn_Does_Not_Emit_OnComplete_To_Consumer_Disposed_During_Replay() {
        val scheduler = ManualScheduler()
        val subject = Observables.replaySubject<Int>(1)
        subject.onNext(1)
        subject.onComplete()

        // same window as above, but for the onComplete that follows replay on an already-completed subject
        lateinit var disposable: Disposable
        val observer = getMockObserver<Int>(onNextHandler = { disposable.dispose() })

        disposable = subject.asObservable().subscribeOn(scheduler)
            .subscribe(observer)
        scheduler.runAll()

        verify(exactly = 1) {
            observer.onNext(1)
        }
        verify(inverse = true) {
            observer.onComplete()
        }
    }

    @Test
    fun subscribeOn_Does_Not_Emit_To_Disposed_Downstream_When_Teardown_Still_Queued() {
        val scheduler = ManualScheduler()
        val observer = getMockObserver<Int>()
        val subject = Observables.publishSubject<Int>()

        val disposable = subject.subscribeOn(scheduler)
            .subscribe(observer)
        // runs the subscribe task plus the queued container.add, so the upstream link now exists
        scheduler.runAll()
        subject.assertSubscriberCount(1)

        // isDisposed flips immediately, but the actual removal from subject is only queued
        disposable.dispose()
        subject.onNext(1)

        verify(inverse = true) {
            observer.onNext(1)
        }
    }

    @Test
    fun subscribeOn_Does_Not_Emit_OnComplete_To_Disposed_Downstream_When_Teardown_Still_Queued() {
        val scheduler = ManualScheduler()
        val observer = getMockObserver<Int>()
        val subject = Observables.publishSubject<Int>()

        val disposable = subject.subscribeOn(scheduler)
            .subscribe(observer)
        scheduler.runAll()
        subject.assertSubscriberCount(1)

        // same deferred-teardown window as above, for onComplete
        disposable.dispose()
        subject.onComplete()

        verify(inverse = true) {
            observer.onComplete()
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