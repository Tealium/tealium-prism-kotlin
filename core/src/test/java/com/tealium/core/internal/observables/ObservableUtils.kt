package com.tealium.core.internal.observables

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.api.listeners.Observer
import io.mockk.spyk
import org.junit.Assert.assertEquals

object ObservableUtils {
    fun <T> getSubject(
        doSubscribeHandler: ((Observer<T>) -> Unit)? = null,
        onNextHandler: ((T) -> Unit)? = null,
    ): Subject<T> {
        return object : BaseSubjectImpl<T>() {
            override fun onAfterSubscribed(observer: Observer<T>) {
                super.onAfterSubscribed(observer)

                doSubscribeHandler?.invoke(observer)
            }

            override fun onNext(value: T) {
                super.onNext(value)

                onNextHandler?.invoke(value)
            }
        }
    }

    private fun <T> getObserver(
        onNextHandler: ((T) -> Unit)? = null,
    ): Observer<T> {
        return object : Observer<T> {
            private var disposable: Disposable? = null
            override fun onNext(value: T) {
                onNextHandler?.invoke(value)
            }
        }
    }

    fun <T> getMockObserver(
        onNextHandler: ((T) -> Unit)? = null,
    ): Observer<T> {
        return spyk(getObserver(onNextHandler))
    }

    fun <T> Subject<T>.assertNoSubscribers() {
        assertSubscriberCount(0)
    }
    fun <T> Subject<T>.assertSubscriberCount(count: Int) {
        assertEquals(count, this.count)
    }
}