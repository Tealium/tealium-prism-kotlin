package com.tealium.prism.core.api.network

import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.pubsub.Observable

/**
 * The [RetryPolicy] returned by a [RequestInterceptor] to decide if and when a failed request is
 * retried.
 *
 * [DoNotRetry] means that the interceptor has no opinion: the next interceptor is asked, and the
 * request completes with its result if none of them proposes a retry. [AfterDelay] and
 * [AfterEvent] propose a retry.
 *
 * @see DoNotRetry
 * @see AfterDelay
 * @see AfterEvent
 */
sealed class RetryPolicy {

    /**
     * [DoNotRetry] completes the request with its result, unless another interceptor proposes a
     * retry.
     *
     * Returned from [RequestInterceptor.shouldRetry], it means that interceptor has no opinion; it
     * is not a veto: the next interceptor is asked.
     */
    object DoNotRetry : RetryPolicy()

    /**
     * [AfterDelay] retries the request once the given [delay] has elapsed.
     *
     * @param delay The time to wait before retrying, in any unit. A negative delay counts as no
     * delay.
     */
    class AfterDelay(val delay: TimeFrame) : RetryPolicy()

    /**
     * [AfterEvent] retries the request when the given [event] emits.
     *
     * The [event] is subscribed to when the retry is decided, and the request is retried on its
     * first emission; the emitted value is ignored. A source that replays a current value on
     * subscription, such as an [ObservableState][com.tealium.prism.core.api.pubsub.ObservableState],
     * retries at once unless filtered.
     *
     * An [event] that completes without emitting, or never emits, leaves the request pending until
     * it is disposed.
     *
     * @param event The [Observable] whose first emission triggers the retry.
     */
    class AfterEvent(val event: Observable<*>) : RetryPolicy()
}
