package com.tealium.core.api.network

import com.tealium.core.api.pubsub.Observable

/**
 * The [RetryPolicy] is used to determine if and when a network request can be retried.
 *
 * @see DoNotRetry
 * @see RetryAfterDelay
 * @see RetryAfterEvent
 */
sealed class RetryPolicy {

    /**
     * This method is used to determine whether or not the request should be retried
     *
     * @return true if the request can be retried; else false.
     */
    abstract fun shouldRetry(): Boolean

    /**
     * [DoNotRetry] signifies that this request cannot be safely retried.
     */
    object DoNotRetry : RetryPolicy() {
        override fun shouldRetry(): Boolean {
            return false
        }
    }

    /**
     * [RetryAfterDelay] signifies that this request can safely be retried, but only after a given time
     * [interval], provided in milliseconds.
     *
     * @param interval The length of time in milliseconds to delay before retrying.
     */
    class RetryAfterDelay(val interval: Long) : RetryPolicy() {
        override fun shouldRetry(): Boolean {
            return true
        }
    }

    /**
     * [RetryAfterEvent] signifies that there is an event that can be used to trigger retrying the
     * network request.
     *
     * The next emission of the [event] [Observable] will trigger the retry of the network request.
     *
     * @param event The [Observable] to subscribe to indicate it is safe to retry
     */
    class RetryAfterEvent<T>(val event: Observable<T>) : RetryPolicy() {
        override fun shouldRetry(): Boolean {
            return true
        }
    }

}
