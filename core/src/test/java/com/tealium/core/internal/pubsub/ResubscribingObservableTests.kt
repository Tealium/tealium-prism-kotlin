package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.Observables
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class ResubscribingObservableTests {

    val source = Observables.just(1, 2, 3, 4, 5)

    @Test
    fun resubscribingWhile_Only_Publishes_First_Emission() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)
        var count = 0
        testTealiumScheduler.execute {
            source.resubscribingWhile { count < 5 }
                .subscribe {
                    onNext(it)
                    count++
                }
        }

        verify(exactly = 5, timeout = 500) {
            onNext(1)
        }
    }

    @Test
    fun resubscribingWhile_Publishes_All_Emissions() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        var count = 0
        Observables.create { observer ->
            if (count < 3) {
                testTealiumScheduler.execute {
                    count++
                    observer.onNext(count)
                }
            }
            Subscription()
        }.resubscribingWhile { count < 3 }
            .subscribe(onNext)

        verify(timeout = 500) {
            onNext(1)
            onNext(2)
            onNext(3)
        }
        confirmVerified(onNext)
    }
}