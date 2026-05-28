package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Consumer
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.subscribe
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SubscribableTests {

    lateinit var subscriptionList: MutableList<Disposable>
    lateinit var container: CompositeDisposable
    lateinit var observer: Observer<Int>
    lateinit var onNext: Consumer<Int>
    lateinit var onComplete: Runnable

    @Before
    fun setUp() {
        subscriptionList = mutableListOf()
        container = DisposableContainer(subscriptionList)
        observer = mockk(relaxed = true)
        onNext = mockk(relaxed = true)
        onComplete = mockk(relaxed = true)
    }

    @Test
    fun subscribe_Observer_With_Container_Automatically_Adds_The_Subscription_To_The_Container() {
        val subject = Observables.publishSubject<Int>()

        subject.subscribe(container, observer)

        assertEquals(1, subscriptionList.size)
    }

    @Test
    fun subscribe_Observer_With_Container_Automatically_Removes_The_Subscription_From_The_Container_On_Completion() {
        val subject = Observables.publishSubject<Int>()

        subject.subscribe(container, observer)
        subject.onComplete()

        assertEquals(0, subscriptionList.size)
    }

    @Test
    fun subscribe_Consumer_With_Container_Automatically_Adds_The_Subscription_To_The_Container() {
        val subject = Observables.publishSubject<Int>()

        subject.subscribe(container) { }

        assertEquals(1, subscriptionList.size)
    }

    @Test
    fun subscribe_Consumer_With_Container_Automatically_Removes_The_Subscription_From_The_Container_On_Completion() {
        val subject = Observables.publishSubject<Int>()

        subject.subscribe(container) { }
        subject.onComplete()

        assertEquals(0, subscriptionList.size)
    }

    @Test
    fun subscribe_Consumer_And_Completion_With_Container_Automatically_Adds_The_Subscription_To_The_Container() {
        val subject = Observables.publishSubject<Int>()

        subject.subscribe(container, { }, { })

        assertEquals(1, subscriptionList.size)
    }

    @Test
    fun subscribe_Consumer_And_Completion_With_Container_Automatically_Removes_The_Subscription_From_The_Container_On_Completion() {
        val subject = Observables.publishSubject<Int>()

        subject.subscribe(container, { }, { })
        subject.onComplete()

        assertEquals(0, subscriptionList.size)
    }

    @Test
    fun subscribe_Consumer_Receives_OnNext_Emissions() {
        val subject = Observables.just(1)

        subject.subscribe(onNext)

        verify { onNext.accept(1) }
    }

    @Test
    fun subscribe_OnComplete_Receives_OnComplete_Emission() {
        val subject = Observables.just(1)

        subject.subscribe({}, onComplete)

        verify {
            onComplete.run()
        }
    }

    @Test
    fun subscribe_Consumer_And_Completion_With_Container_Invokes_OnComplete_On_Completion() {
        val subject = Observables.just(1)

        subject.subscribe(container, { }, onComplete)

        verify {
            onComplete.run()
        }
    }
}
