package com.tealium.core.api.pubsub

/**
 * A [Subscribable] implementation whereby only a single result is expected to be emitted to the subscriber.
 */
interface Single<T> : Subscribable<T>