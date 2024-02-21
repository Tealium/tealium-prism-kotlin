package com.tealium.core.internal.observables

import com.tealium.core.api.listeners.Subscribable
import com.tealium.core.internal.observables.impl.BufferedObservable
import com.tealium.core.internal.observables.impl.DistinctObservable
import com.tealium.core.internal.observables.impl.FilterObservable
import com.tealium.core.internal.observables.impl.FlatMapLatestObservable
import com.tealium.core.internal.observables.impl.FlatMapObservable
import com.tealium.core.internal.observables.impl.MapNotNullObservable
import com.tealium.core.internal.observables.impl.MapObservable
import com.tealium.core.internal.observables.impl.ObserveOnObservable
import com.tealium.core.internal.observables.impl.SubscribeOnObservable
import com.tealium.core.internal.observables.impl.TakeObservable
import com.tealium.core.internal.observables.impl.TakeWhileObservable
import java.util.Objects
import java.util.concurrent.ExecutorService

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
     * Returns an observable that performs the subscription on the given [executor]
     */
    fun subscribeOn(executor: ExecutorService): Observable<T> {
        return SubscribeOnObservable(this, executor)
    }

    /**
     * Returns an observable that propagates emissions downstream on the given [executor]
     */
    fun observeOn(executor: ExecutorService): Observable<T> {
        return ObserveOnObservable(this, executor)
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
}