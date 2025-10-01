package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class ForEachObservableTests {

    @Test
    fun forEach_CallsSpecifiedFunction_ForEachItem() {
        val subject = Observables.publishSubject<String>()
        val callback = mockk<(String) -> Unit>(relaxed = true)
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        subject.forEach(callback)
            .subscribe(onNext)

        subject.onNext("One")
        verify {
            callback("One")
            onNext("One")
        }
    }

    @Test
    fun forEach_Dispose_StopsEmitting() {
        val subject = Observables.publishSubject<String>()
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val subscription = subject.forEach(::println)
            .subscribe(onNext)

        subject.onNext("One")

        subscription.dispose()
        subject.onNext("Two")

        subject.assertNoSubscribers()
        verify {
            onNext("One")
        }
        verify(inverse = true) {
            onNext("Two")
        }
    }
}