package com.tealium.core.internal.observables

import com.tealium.core.internal.observables.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FlatMapLatestObservableTests {

    @Test
    fun flatMapLatest_EmitsOnlyLatest() {
        val observerExecutorService: ExecutorService = Executors.newSingleThreadExecutor()

        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.just(1, 2, 3)
            .flatMapLatest {
                Observables.just(it)
                    .observeOn(observerExecutorService)
            }.subscribe(onNext)

        verify(inverse = true, timeout = 1000) {
            onNext(1)
            onNext(2)
        }
        verify(timeout = 1000) {
            onNext(3)
        }
    }

    @Test
    fun flatMapLatest_Dispose_StopsEmitting() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = subject.flatMapLatest {
            Observables.just(it, it + 1)
        }.subscribe(onNext)

        subject.onNext(1)
        subscription.dispose()
        subject.onNext(3)

        subject.assertNoSubscribers()
        verify {
            onNext(1)
            onNext(2)
        }
        verify(inverse = true) {
            onNext(3)
            onNext(4)
        }
    }
}