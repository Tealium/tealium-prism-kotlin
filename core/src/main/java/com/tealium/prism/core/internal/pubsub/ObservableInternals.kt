package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.Subscribable
import com.tealium.prism.core.api.pubsub.SubscribableState
import com.tealium.prism.core.internal.pubsub.impl.ObservableStateImpl

fun <T> Subscribable<T>.asObservable() : Observable<T> {
    return object : Observable<T>, Subscribable<T> by this { }
}

fun <T> SubscribableState<T>.asObservableState() : ObservableState<T> {
    return ObservableStateImpl(this)
}

/**
 * An interface for typical intermediate [Observer] operators, where they are both an [Observer] and
 * upstream and downstream aware.
 */
interface LinkableObserver<T> : UpstreamLinkable, Observer<T>

/**
 * Gets the downstream [Observer] via [downstreamSupplier], subscribes it to [this] as the source,
 * and [link][UpstreamLinkable.setUpstream]s it to the downstream.
 *
 * @param downstreamSupplier the block to create the downstream [LinkableObserver]
 * @return the downstream [LinkableObserver]
 */
inline fun <T> Observable<T>.subscribeAndLink(
    downstreamSupplier: () -> LinkableObserver<T>
): LinkableObserver<T> {
    val downstream = downstreamSupplier.invoke()
    val upstream = this.subscribe(downstream)
    downstream.setUpstream(upstream)

    return downstream
}

inline fun <T> Observable<T>.subscribeWrapAndLink(
    downstreamSupplier: () -> Observer<T>
): LinkableObserver<T> =
    subscribeAndLink { downstreamSupplier.invoke().asLinkableObserver() }

/**
 * Wraps the current [Observer] implementation in a [LinkableObserver] that automatically takes care
 * of
 *  - preventing `onNext` emissions if disposed
 *  - preventing multiple `onComplete` emissions downstream
 *  - disposing the upstream on complete
 */
fun <T> Observer<T>.asLinkableObserver() : LinkableObserver<T> =
    DisposableObserver(this)
