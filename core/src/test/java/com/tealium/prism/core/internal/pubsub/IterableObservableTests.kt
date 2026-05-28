package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class IterableObservableTests {

    private lateinit var observer: Observer<Int>

    @Before
    fun setUp() {
        observer = mockk(relaxed = true)
    }

    @Test
    fun just_Emits_All_On_Subscribe_And_Completes() {
        Observables.just(1, 2, 3, 4, 5)
            .subscribe(observer)

        verifyOrder {
            observer.onNext(1)
            observer.onNext(2)
            observer.onNext(3)
            observer.onNext(4)
            observer.onNext(5)
            observer.onComplete()
        }
    }

    @Test
    fun just_Single_Emits_One_On_Subscribe_And_Completes() {
        Observables.just(1)
            .subscribe(observer)

        verifyOrder {
            observer.onNext(1)
            observer.onComplete()
        }
    }

    @Test
    fun just_Subscribe_Returns_Completed_Disposable() {
        val disposable = Observables.just(1)
            .subscribe(observer)

        assertSame(Disposables.disposed(), disposable)
    }
}