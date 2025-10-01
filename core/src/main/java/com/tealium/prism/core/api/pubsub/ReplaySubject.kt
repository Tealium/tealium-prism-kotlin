package com.tealium.prism.core.api.pubsub

/**
 * A [ReplaySubject] is a specialized [Subject] that also maintains the latest N emissions
 * in a cache. Upon subscription, all values in the cache will be emitted to the subscriber.
 *
 * New values can be emitted to subscribers via the [Observer.onNext] method.
 */
interface ReplaySubject<T>: Subject<T> {

    /**
     * Removes all currently cached values from this [Subject]. Future subscribers will no longer
     * received the current entries upon subscribing.
     */
    fun clear()

    /**
     * Resizes the max cache size to the given [size]. If the new [size] is smaller than the current
     * cache size, then old entries will be evicted to make space.
     *
     * Negative values will be treated as an unbounded replay cache.
     *
     * @param size the new max size of the replay cache
     */
    fun resize(size: Int)

    /**
     * Attempts to return the last item that was published.
     *
     * @return the last item that was published, or null if nothing has been published yet
     */
    fun last(): T?
}