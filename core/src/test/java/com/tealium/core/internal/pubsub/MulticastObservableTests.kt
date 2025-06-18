package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.Observables
import com.tealium.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.core.internal.pubsub.ObservableUtils.assertSubscriberCount
import com.tealium.core.internal.pubsub.impl.MulticastObservable
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test


class MulticastObservableTests {

    @Test
    fun subscribe_Only_Subscribes_To_Source_Observable_Once() {
        val source = Observables.publishSubject<Int>()
        val observer1 = mockk<(Int) -> Unit>(relaxed = true)
        val observer2 = mockk<(Int) -> Unit>(relaxed = true)

        val multicast = MulticastObservable(source)
        multicast.subscribe(observer1)
        multicast.subscribe(observer2)

        source.assertSubscriberCount(1)
    }

    @Test
    fun multicast_Does_Not_Subscribe_To_Source_Until_First_Observer() {
        val source = Observables.publishSubject<Int>()
        val observer = mockk<(Int) -> Unit>(relaxed = true)

        val multicast = MulticastObservable(source)
        source.assertNoSubscribers()

        multicast.subscribe(observer)
        source.assertSubscriberCount(1)
    }

    @Test
    fun multicast_Emits_To_All_Downstream_Observers() {
        val source = Observables.publishSubject<Int>()
        val observer1 = mockk<(Int) -> Unit>(relaxed = true)
        val observer2 = mockk<(Int) -> Unit>(relaxed = true)

        val multicast = MulticastObservable(source)
        multicast.subscribe(observer1)
        multicast.subscribe(observer2)

        source.onNext(1)

        verify {
            observer1.invoke(1)
            observer2.invoke(1)
        }
    }

    @Test
    fun share_Does_Not_Replay_Emissions_To_Late_Subscribing_Observers() {
        val source = Observables.publishSubject<Int>()
        val observer1 = mockk<(Int) -> Unit>(relaxed = true)
        val observer2 = mockk<(Int) -> Unit>(relaxed = true)

        val shared = source.share()
        shared.subscribe(observer1)

        source.onNext(1)
        shared.subscribe(observer2)

        verify {
            observer1.invoke(1)
        }
        verify(inverse = true) {
            observer2.invoke(1)
        }
    }

    @Test
    fun shareReplay_Does_Replay_Emissions_To_Late_Subscribing_Observers() {
        val source = Observables.publishSubject<Int>()
        val observer1 = mockk<(Int) -> Unit>(relaxed = true)
        val observer2 = mockk<(Int) -> Unit>(relaxed = true)

        val replay = source.share(2)
        replay.subscribe(observer1)

        source.onNext(1)
        source.onNext(2)
        replay.subscribe(observer2)

        verify {
            observer1.invoke(1)
            observer2.invoke(1)

            observer1.invoke(2)
            observer2.invoke(2)
        }
    }

    @Test
    fun shareReplay_Only_Replays_Latest_X_Emissions_To_Late_Subscribing_Observers() {
        val source = Observables.publishSubject<Int>()
        val observer1 = mockk<(Int) -> Unit>(relaxed = true)
        val observer2 = mockk<(Int) -> Unit>(relaxed = true)

        val replay = source.share(1)
        replay.subscribe(observer1)

        source.onNext(1)
        source.onNext(2)
        replay.subscribe(observer2)

        verify {
            observer1.invoke(1)
            observer1.invoke(2)
            observer2.invoke(2)
        }
        verify(inverse = true) {
            observer2.invoke(1)
        }
    }

    @Test
    fun shareReplay_Replays_Immediate_Emissions_To_Late_Subscribing_Observers() {
        val source = Observables.just(1, 2)
        val observer1 = mockk<(Int) -> Unit>(relaxed = true)
        val observer2 = mockk<(Int) -> Unit>(relaxed = true)

        val replay = source.share(2)
        replay.subscribe(observer1)
        replay.subscribe(observer2)

        verify {
            observer1.invoke(1)
            observer1.invoke(2)
            observer2.invoke(1)
            observer2.invoke(2)
        }
    }

    @Test
    fun dispose_Stops_Emissions_To_Specific_Observers() {
        val subject = Observables.publishSubject<Int>()
        val observer1 = mockk<(Int) -> Unit>(relaxed = true)
        val observer2 = mockk<(Int) -> Unit>(relaxed = true)

        val multicast = MulticastObservable(subject)
        val disposable1 = multicast.subscribe(observer1)
        multicast.subscribe(observer2)

        disposable1.dispose()

        subject.onNext(1)

        verify(inverse = true) {
            observer1.invoke(1)
        }
        verify {
            observer2.invoke(1)
        }
    }

    @Test
    fun dispose_Disposes_Source_Subscription_When_No_Observers_Left() {
        val subject = Observables.publishSubject<Int>()
        val observer1 = mockk<(Int) -> Unit>(relaxed = true)
        val observer2 = mockk<(Int) -> Unit>(relaxed = true)

        val multicast = MulticastObservable(subject)
        val disposable1 = multicast.subscribe(observer1)
        val disposable2 = multicast.subscribe(observer2)

        subject.assertSubscriberCount(1)

        disposable1.dispose()
        disposable2.dispose()

        subject.assertNoSubscribers()
    }
}