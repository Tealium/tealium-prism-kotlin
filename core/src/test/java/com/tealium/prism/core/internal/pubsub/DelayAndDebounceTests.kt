package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.tests.common.ManualScheduler
import com.tealium.tests.common.VirtualTimeScheduler
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class DelayAndDebounceTests {

    private lateinit var scheduler: VirtualTimeScheduler
    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        scheduler = VirtualTimeScheduler()
        observer = mockk(relaxed = true)
    }

    @Test
    fun delay_Emits_Each_Value_After_The_Delay() {
        Observables.just(1, 2, 3)
            .delay(10.milliseconds, scheduler)
            .subscribe(observer)

        verify(inverse = true) { observer.onNext(any()) }

        scheduler.advanceTimeBy(9)
        verify(inverse = true) { observer.onNext(any()) }

        scheduler.advanceTimeBy(1)
        verifyOrder {
            observer.onNext(1)
            observer.onNext(2)
            observer.onNext(3)
        }
    }

    @Test
    fun delay_Notifies_On_Provided_Scheduler() {
        val manualScheduler = ManualScheduler()

        Observables.just(1, 2, 3)
            .delay(1.milliseconds, manualScheduler)
            .subscribe(observer)

        verify(inverse = true) { observer.onNext(any()) }

        manualScheduler.runAll()
        verifyOrder {
            observer.onNext(1)
            observer.onNext(2)
            observer.onNext(3)
        }
    }

    @Test
    fun delay_Emits_Synchronously_When_Delay_Is_Zero_And_Already_On_The_Scheduler() {
        val received = mutableListOf<Int>()
        var receivedRightAfterSubscribing: List<Int> = emptyList()
        val done = CountDownLatch(1)

        testTealiumScheduler.execute {
            Observables.just(1, 2, 3)
                .delay(0.milliseconds, testTealiumScheduler)
                .subscribe { received.add(it) }
            receivedRightAfterSubscribing = received.toList()
            done.countDown()
        }

        assertTrue("Task not executed", done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(1, 2, 3), receivedRightAfterSubscribing)
    }

    @Test
    fun delay_Completes_Subscription_When_Upstream_Is_Completed() {
        Observables.just(1, 2, 3)
            .delay(0.milliseconds, scheduler)
            .subscribe(observer)

        verifyOrder {
            observer.onNext(1)
            observer.onNext(2)
            observer.onNext(3)
            observer.onComplete()
        }
    }

    @Test
    fun delay_Does_Not_Dispose_Subscription_When_Upstream_Is_Not_Disposed() {
        val subject = Observables.publishSubject<Int>()

        val subscription = subject.delay(0.milliseconds, scheduler)
            .subscribe(observer)
        subject.onNext(1)

        verify { observer.onNext(1) }
        assertFalse(subscription.isDisposed)
    }

    @Test
    fun delay_Dispose_Cancels_Pending_Emissions_And_Cleans_Subscriptions() {
        val subject = Observables.publishSubject<Int>()

        val subscription = subject.delay(10.milliseconds, scheduler)
            .subscribe(observer)
        subject.onNext(1)
        subscription.dispose()
        scheduler.advanceTimeBy(10)

        subject.assertNoSubscribers()
        verify(inverse = true) { observer.onNext(any()) }
    }

    @Test
    fun debounce_Emits_Only_Last_Element_Of_A_Quick_Sequence_When_There_Is_A_Delay() {
        Observables.just(1, 2, 3)
            .debounce(10.milliseconds, scheduler)
            .subscribe(observer)

        verify(inverse = true) { observer.onNext(any()) }

        scheduler.advanceTimeBy(10)
        verify(exactly = 1) { observer.onNext(any()) }
        verify { observer.onNext(3) }
    }

    @Test
    fun debounce_Restarts_The_Delay_At_Each_Element() {
        val subject = Observables.publishSubject<Int>()
        subject.debounce(10.milliseconds, scheduler)
            .subscribe(observer)

        subject.onNext(1)
        scheduler.advanceTimeBy(9)
        subject.onNext(2)
        scheduler.advanceTimeBy(9)
        verify(inverse = true) { observer.onNext(any()) }

        scheduler.advanceTimeBy(1)
        verify(exactly = 1) { observer.onNext(any()) }
        verify { observer.onNext(2) }
    }

    @Test
    fun debounce_Observes_On_Provided_Scheduler() {
        val manualScheduler = ManualScheduler()

        Observables.just(1, 2, 3)
            .debounce(1.milliseconds, manualScheduler)
            .subscribe(observer)

        verify(inverse = true) { observer.onNext(any()) }

        manualScheduler.runAll()
        verify { observer.onNext(3) }
    }

    @Test
    fun debounce_Completes_When_Upstream_Has_Completed() {
        Observables.just(1, 2, 3)
            .debounce(0.milliseconds, scheduler)
            .subscribe(observer)

        verifyOrder {
            observer.onNext(1)
            observer.onNext(2)
            observer.onNext(3)
            observer.onComplete()
        }
    }

    @Test
    fun debounce_Does_Not_Dispose_Subscription_When_Upstream_Is_Not_Disposed() {
        val subject = Observables.publishSubject<Int>()

        val subscription = subject.debounce(0.milliseconds, scheduler)
            .subscribe(observer)
        subject.onNext(1)

        verify { observer.onNext(1) }
        assertFalse(subscription.isDisposed)
    }

    @Test
    fun delay_Dispose_Before_Execution_Drops_The_Element_When_Delay_Is_Zero() {
        assertDisposeBeforeExecutionDropsTheElement { subject, manualScheduler ->
            subject.delay(0.milliseconds, manualScheduler)
        }
    }

    @Test
    fun debounce_Dispose_Before_Execution_Drops_The_Element_When_Delay_Is_Zero() {
        assertDisposeBeforeExecutionDropsTheElement { subject, manualScheduler ->
            subject.debounce(0.milliseconds, manualScheduler)
        }
    }

    @Test
    fun debounce_Dispose_Cancels_Pending_Emission_And_Cleans_Subscriptions() {
        val subject = Observables.publishSubject<Int>()

        val subscription = subject.debounce(10.milliseconds, scheduler)
            .subscribe(observer)
        subject.onNext(1)
        subscription.dispose()
        scheduler.advanceTimeBy(10)

        subject.assertNoSubscribers()
        verify(inverse = true) { observer.onNext(any()) }
    }

    @Test
    fun debounce_Delivers_A_Value_Emitted_Reentrantly_Before_Upstream_Completes() {
        val subject = Observables.publishSubject<Int>()
        val events = mutableListOf<String>()
        subject.debounce(10.milliseconds, scheduler)
            .subscribe(object : Observer<Int> {
                override fun onNext(value: Int) {
                    events.add("next $value")
                    if (value == 1) {
                        subject.onNext(2)
                        subject.onComplete()
                    }
                }

                override fun onComplete() {
                    events.add("complete")
                }
            })

        subject.onNext(1)
        scheduler.advanceTimeBy(10)
        scheduler.advanceTimeBy(10)

        assertEquals(listOf("next 1", "next 2", "complete"), events)
    }

    @Test
    fun delay_Schedules_Positive_Sub_Millisecond_Delays() {
        val recordingScheduler = mockk<Scheduler>(relaxed = true)
        val subMillisecond = TimeFrame(500, TimeUnit.MICROSECONDS)

        Observables.just(1)
            .delay(subMillisecond, recordingScheduler)
            .subscribe(observer)

        verify(exactly = 1) { recordingScheduler.schedule(subMillisecond, any()) }
    }

    @Test
    fun debounce_Does_Not_Emit_Subsequent_Synchronous_Event_After_Observer_Side_Effect_Disposal() {
        assertNoEmissionAfterSideEffectDisposal { it.debounce(0.milliseconds, scheduler) }
    }

    @Test
    fun delay_Delivers_Off_Scheduler_When_Delay_Is_Zero() {
        assertDeliversOffScheduler { it.delay(0.milliseconds, testTealiumScheduler) }
    }

    @Test
    fun debounce_Delivers_Off_Scheduler_When_Delay_Is_Zero() {
        assertDeliversOffScheduler { it.debounce(0.milliseconds, testTealiumScheduler) }
    }

    /**
     * On a scheduler that defers the zero delay tasks, disposing before they run drops the element.
     */
    private fun assertDisposeBeforeExecutionDropsTheElement(
        applyOperator: (Observable<Int>, ManualScheduler) -> Observable<Int>
    ) {
        val manualScheduler = ManualScheduler()
        val subject = Observables.publishSubject<Int>()

        val subscription = applyOperator(subject, manualScheduler)
            .subscribe(observer)
        subject.onNext(1)
        subscription.dispose()
        manualScheduler.runAll()

        subject.assertNoSubscribers()
        verify(inverse = true) { observer.onNext(any()) }
    }

    /**
     * Emits an element from a background thread and expects it to be delivered.
     * Completion isn't emitted off-scheduler: operators expect their upstream events on a single scheduler.
     */
    private fun assertDeliversOffScheduler(operator: (Observable<Int>) -> Observable<Int>) {
        val delivered = CountDownLatch(1)
        val received = mutableListOf<Int>()
        val subject = Observables.publishSubject<Int>()

        operator(subject)
            .subscribe {
                received.add(it)
                delivered.countDown()
            }
        thread {
            subject.onNext(1)
        }

        assertTrue("Element not delivered", delivered.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(1), received)
    }

    /**
     * Asserts that the observable produced by [applyOperator] does not emit a second event after
     * the observer disposes on the first one.
     *
     * The upstream emits all the [upstreamValues] synchronously, without checking if it was disposed.
     */
    private fun assertNoEmissionAfterSideEffectDisposal(
        upstreamValues: List<Int> = listOf(1, 2),
        applyOperator: (Observable<Int>) -> Observable<Int>
    ) {
        val disposable = Disposables.composite()
        val received = mutableListOf<Int>()
        var upstreamObserver: Observer<Int>? = null
        val observable = Observables.create<Int> { observer ->
            upstreamObserver = observer
            Disposables.composite()
        }
        applyOperator(observable)
            .subscribe {
                received.add(it)
                disposable.dispose()
            }.addTo(disposable)

        // Synchronous observer calls, done without checking if the subscription is already disposed.
        upstreamValues.forEach { upstreamObserver?.onNext(it) }

        assertEquals(listOf(1), received)
    }
}
