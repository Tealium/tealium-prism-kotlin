package com.tealium.core.api.pubsub

import com.tealium.core.api.misc.Scheduler
import com.tealium.core.internal.pubsub.SingleImpl
import com.tealium.core.internal.pubsub.impl.BufferedObservable
import com.tealium.core.internal.pubsub.impl.DistinctObservable
import com.tealium.core.internal.pubsub.impl.FilterObservable
import com.tealium.core.internal.pubsub.impl.FlatMapLatestObservable
import com.tealium.core.internal.pubsub.impl.FlatMapObservable
import com.tealium.core.internal.pubsub.impl.MapNotNullObservable
import com.tealium.core.internal.pubsub.impl.MapObservable
import com.tealium.core.internal.pubsub.impl.MulticastObservable
import com.tealium.core.internal.pubsub.impl.ObservableStateValue
import com.tealium.core.internal.pubsub.impl.ObserveOnObservable
import com.tealium.core.internal.pubsub.impl.ResubscribingObservable
import com.tealium.core.internal.pubsub.impl.StartWithObservable
import com.tealium.core.internal.pubsub.impl.SubscribeOnObservable
import com.tealium.core.internal.pubsub.impl.TakeObservable
import com.tealium.core.internal.pubsub.impl.TakeWhileObservable
import java.util.Objects

/**
 * Base class for observable implementations - provides methods for built in intermediate operations.
 */
interface Observable<T> : Subscribable<T> {

    /**
     * Returns an observable that filters out emissions that do not match the given [predicate]
     */
    fun filter(predicate: (T) -> Boolean): Observable<T> {
        return FilterObservable(this, predicate)
    }

    /**
     * Returns an observable that emits downstream up until the [predicate] returns false.
     */
    fun takeWhile(predicate: (T) -> Boolean): Observable<T> {
        return TakeWhileObservable(this, predicate)
    }

    /**
     * Returns an observable that emits only the specified number of events given by the provided [count]
     */
    fun take(count: Int): Observable<T> {
        return TakeObservable(this, count)
    }

    /**
     * Returns an observable that will buffer emissions until the buffer is full, at which point they
     * will be emitted downstream.
     */
    fun buffered(count: Int): Observable<T> {
        return BufferedObservable(this, count)
    }

    /**
     * Returns an observable that applies the given [transform] to each emission before passing it
     * downstream.
     */
    fun <R> map(transform: (T) -> R): Observable<R> {
        return MapObservable(this, transform)
    }

    /**
     * Returns an observable that applies the given [transform] to each emission before passing it
     * downstream. Only emissions that are non-null after the application of the [transform] will be
     * emitted downstream.
     */
    fun <R> mapNotNull(transform: (T) -> R?): Observable<R> {
        return MapNotNullObservable(this, transform)
    }

    /**
     * Returns an observable that applies the given [transform] to the source emissions to produce
     * new observables - all emissions from the resulting observables will be emitted downstream.
     */
    fun <R> flatMap(transform: (T) -> Observable<R>): Observable<R> {
        return FlatMapObservable(this, transform)
    }

    /**
     * Returns an observable that applies the given [transform] to the source emissions to produce
     * a new observable - only emissions from the latest observable created by the [transform] will
     * be emitted downstream.
     */
    fun <R> flatMapLatest(transform: (T) -> Observable<R>): Observable<R> {
        return FlatMapLatestObservable(this, transform)
    }

    /**
     * Returns an observable that only emits downstream when the newest emissions is not equal to
     * the previous emission.
     * Emissions will be compared using standard [Objects.equals]
     */
    fun distinct(): Observable<T> {
        return distinct(Objects::equals)
    }

    /**
     * Returns an observable that only emits downstream when the newest emissions is not equal to
     * the previous emission.
     * Emissions will be compared using the provided [isEquals] function
     */
    fun distinct(isEquals: (T, T) -> Boolean): Observable<T> {
        return DistinctObservable(this, isEquals)
    }

    /**
     * Returns an observable that will propagate all source emissions downstream from this observable
     * and from the given [other].
     */
    fun merge(other: Observable<T>): Observable<T> {
        return Observables.merge(this, other)
    }

    /**
     * Returns an observable that will call the given [block] with each source emission, before
     * passing the original emission downstream.
     */
    fun forEach(block: (T) -> Unit): Observable<T> {
        return MapObservable(this) {
            block(it)
            it
        }
    }

    /**
     * Returns an observable that performs the subscription on the given [scheduler]
     */
    fun subscribeOn(scheduler: Scheduler): Observable<T> {
        return SubscribeOnObservable(this, scheduler)
    }

    /**
     * Returns an observable that propagates emissions downstream on the given [scheduler]
     */
    fun observeOn(scheduler: Scheduler): Observable<T> {
        return ObserveOnObservable(this, scheduler)
    }

    /**
     * Returns an observable that combines the emissions of this observable the given [other]. The
     * downstream emission is the result of applying the [combiner] function to the latest emissions
     * of both observables - and are only possible once both this and the [other] have emitted at
     * least one value.
     */
    fun <T2, R> combine(other: Observable<T2>, combiner: (T, T2) -> R): Observable<R> {
        return Observables.combine(this, other, combiner)
    }


    /**
     * Returns an observable that will emit all the given [item] values before making a subscription
     * to the source observable.
     */
    fun startWith(vararg item: T): Observable<T> {
        return StartWithObservable(this, item.asIterable())
    }

    /**
     * Returns an observable that will emit values in a possibly asynchronous manner determined by
     * the given [block].
     *
     * @param block a block of code, to be executed with the next value from the source, along with
     * the observer with which to emit downstream.
     */
    fun <R> async(block: (T, Observer<R>) -> Disposable): Observable<R> {
        return flatMap { value ->
            Observables.async { observer ->
                block(value, observer)
            }
        }
    }

    /**
     * Returns an observable that will emit values in a possibly asynchronous manner determined by
     * the given [block].
     *
     * @param block a block of code, to be executed with the next value from the source, along with
     * the observer with which to emit downstream.
     */
    fun <R> callback(block: (T, Observer<R>) -> Unit): Observable<R> {
        return flatMap { value ->
            Observables.callback { observer ->
                block(value, observer)
            }
        }
    }

    /**
     * Returns an observable that will continually resubscribe until the [predicate] returns false.
     *
     * @param predicate The test to decide when to stop subscribing.
     */
    fun resubscribingWhile(predicate: (T) -> Boolean): Observable<T> {
        return ResubscribingObservable(this, predicate)
    }

    /**
     * Converts a standard [Observable] into one that maintains the current value as state.
     *
     * The [ObservableState.value] of the returned [Observable] is derived from the given [observableState]
     * until there have been emissions from the source.
     */
    fun withState(observableState: ObservableState<T>): ObservableState<T> {
        return ObservableStateValue(this, observableState::value)
    }

    /**
     * Converts a standard [Observable] into one that maintains the current value as state.
     *
     * The [ObservableState.value] of the returned [Observable] is derived from the given [valueSupplier]
     * until there have been emissions from the source.
     */
    fun withState(valueSupplier: () -> T): ObservableState<T> {
        return ObservableStateValue(this, valueSupplier)
    }

    /**
     * Converts this [Observable] to a [Single] subscribing on the given [Scheduler]
     *
     * A [take] and [subscribeOn] are applied to the source [Observable] automatically to
     * enforce only a single emission.
     */
    fun asSingle(scheduler: Scheduler): Single<T> {
        return SingleImpl(this, scheduler)
    }

    /**
     * Returns an [Observable] that will share a single connection to the source [Observable] (this).
     *
     * This [Observable] will not subscribe to the source until the first observer subscribes, and
     * no emissions are replayed to late-subscribing observers.
     */
    fun share() : Observable<T> {
        return MulticastObservable(this, Observables.publishSubject())
    }

    /**
     * Returns an [Observable] that will share a single connection to the source [Observable] (this).
     *
     * This [Observable] will not subscribe to the source until the first observer subscribes.
     * Emissions are replayed to late-subscribing observers according to the given [replay]
     *
     * @param replay The number of emissions to cache and replay to late-subscribing observers
     */
    fun share(replay: Int) : Observable<T> {
        return MulticastObservable(this, Observables.replaySubject(replay))
    }
}