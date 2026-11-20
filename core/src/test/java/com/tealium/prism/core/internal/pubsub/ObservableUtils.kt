package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.Subject
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

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
        onCompleteHandler: (() -> Unit)? = null,
    ): Observer<T> {
        return object : Observer<T> {
            private var disposable: Disposable? = null
            override fun onNext(value: T) {
                onNextHandler?.invoke(value)
            }

            override fun onComplete() {
                onCompleteHandler?.invoke()
            }
        }
    }

    fun <T> getMockObserver(
        onCompleteHandler: (() -> Unit)? = null,
        onNextHandler: ((T) -> Unit)? = null,
    ): Observer<T> {
        return spyk(getObserver(onNextHandler, onCompleteHandler))
    }

    /**
     * Returns an [Observer] that appends `next:<value>` and `complete` to the [log], each
     * prefixed with `<name>:` when a [name] is given, and calls [onValue] after logging a value.
     */
    fun <T> recordingObserver(
        log: MutableList<String>,
        name: String? = null,
        onValue: (T) -> Unit = {}
    ): Observer<T> {
        val prefix = if (name != null) "$name:" else ""
        return object : Observer<T> {
            override fun onNext(value: T) {
                log.add("${prefix}next:$value")
                onValue(value)
            }

            override fun onComplete() {
                log.add("${prefix}complete")
            }
        }
    }

    fun <T> Subject<T>.assertNoSubscribers() {
        assertSubscriberCount(0)
    }
    fun <T> Subject<T>.assertSubscriberCount(count: Int) {
        assertEquals(count, this.count)
    }

    /**
     * Asserts that a subscriber that disposes itself on its first value is unregistered when
     * subscribe returns, and receives nothing emitted later. The subject must emit a value on
     * subscription.
     */
    fun Subject<Int>.assertSelfDisposingSubscriberIsReleased() {
        val received = subscribeSelfDisposing(disposesOnNext = true)

        assertNoSubscribers()
        onNext(LATER_VALUE)
        assertFalse(received.contains(LATER_VALUE))
    }

    /**
     * Guards [assertSelfDisposingSubscriberIsReleased]: asserts that a subscriber that doesn't
     * dispose itself stays registered and receives what is emitted later.
     */
    fun Subject<Int>.assertStayingSubscriberIsRetained() {
        val received = subscribeSelfDisposing(disposesOnNext = false)

        assertSubscriberCount(1)
        onNext(LATER_VALUE)
        assertTrue(received.contains(LATER_VALUE))
    }

    private fun Subject<Int>.subscribeSelfDisposing(disposesOnNext: Boolean): List<Int> {
        val received = mutableListOf<Int>()
        subscribeAndLink {
            object : LinkableObserver<Int>, UpstreamLinkable by UpstreamLinkableImpl() {
                override fun onNext(value: Int) {
                    received.add(value)
                    if (disposesOnNext) dispose()
                }

                override fun onComplete() {}
            }
        }
        return received
    }

    private const val LATER_VALUE = 99
}
