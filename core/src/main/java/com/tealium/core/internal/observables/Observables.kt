package com.tealium.core.internal.observables

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.observables.impl.CallbackObservable
import com.tealium.core.internal.observables.impl.CombineObservable
import com.tealium.core.internal.observables.impl.CustomObservable
import com.tealium.core.internal.observables.impl.IterableCombineObservable
import com.tealium.core.internal.observables.impl.IterableObservable

object Observables {
    @JvmStatic
    fun <T> create(subscriptionHandler: (Observer<T>) -> Disposable): Observable<T> {
        return CustomObservable<T>(null, subscriptionHandler)
    }

    /**
     * Returns an observable subject that emits values to any subscribers at the time of the
     * emission.
     */
    @JvmStatic
    fun <T> publishSubject(): Subject<T> {
        return PublishSubject()
    }

    /**
     * Returns an observable subject that emits values to any subscribers at the time of the
     * emission.
     *
     * When subscribing, new subscribers will receive the latest emission if there has already
     * been one
     */
    @JvmStatic
    fun <T> stateSubject(initialValue: T): StateSubject<T> {
        return StateSubjectImpl(initialValue)
    }

    /**
     * Returns an observable subject that emits values to any subscribers at the time of the
     * emission.
     *
     * When subscribing, new subscribers will receive up to [cacheSize] past emissions, in
     * sequential order.
     *
     * @param cacheSize The maximum number of entries to replay to subscribers. Negative values will
     * be treated as an unbounded cache.
     */
    @JvmStatic
    fun <T> replaySubject(cacheSize: Int): ReplaySubject<T> {
        return ReplaySubjectImpl(cacheSize)
    }

    /**
     * Returns an observable subject that emits values to any subscribers at the time of the
     * emission.
     *
     * When subscribing, new subscribers will receive all past emissions, in sequential order.
     * The cache size is unbounded.
     */
    @JvmStatic
    fun <T> replaySubject(): ReplaySubject<T> {
        return ReplaySubjectImpl(-1)
    }

    /**
     * Returns an observable that emits only the given [item] to any observer that subscribes.
     */
    @JvmStatic
    fun <T> just(item: T): Observable<T> {
        return IterableObservable(listOf(item))
    }

    /**
     * Returns an observable that emits only the given [items] to any observer that subscribes.
     */
    @JvmStatic
    fun <T> just(vararg items: T): Observable<T> {
        return IterableObservable(items.asList())
    }

    /**
     * Returns an observable that emits each item from the given [items] to any observer that
     * subscribes.
     */
    @JvmStatic
    fun <T> fromIterable(items: Iterable<T>): Observable<T> {
        return IterableObservable(items)
    }

    /**
     * Returns an observable that does not emit anything.
     */
    @JvmStatic
    fun <T> empty(): Observable<T> {
        return just()
    }

    /**
     * Returns an observable that emits all of the source emissions from all of the given
     * [observables].
     */
    @JvmStatic
    fun <T> merge(vararg observables: Observable<T>): Observable<T> {
        return just(*observables).flatMap { it }
    }

    /**
     * Returns an observable that emits all of the source emissions from all of the given
     * [observables].
     */
    @JvmStatic
    fun <T> merge(observables: Iterable<Observable<T>>): Observable<T> {
        return fromIterable(observables).flatMap { it }
    }

    /**
     * Returns an observable that combines the emissions of this observable the given [other]. The
     * downstream emission is the result of applying the [combiner] function to the latest emissions
     * of both observables - and are only possible once both this and the [other] have emitted at
     * least one value.
     */
    @JvmStatic
    fun <T1, T2, R> combine(
        observable1: Observable<T1>,
        observable2: Observable<T2>,
        combiner: (T1, T2) -> R
    ): Observable<R> {
        return CombineObservable(observable1, observable2, combiner)
    }

    /**
     * Returns an observable that combines the emissions of all [sources]. The downstream
     * emission is the result of applying the [combiner] function to the latest emissions
     * of all sources - and are only possible once all [sources] have emitted at least one
     * value.
     */
    @JvmStatic
    fun <T, R> combine(
        sources: Iterable<Observable<T>>,
        combiner: (Iterable<T>) -> R
    ): Observable<R> {
        return if (sources.count() == 0) {
            just(combiner(listOf()))
        } else {
            IterableCombineObservable(sources, combiner)
        }
    }

    /**
     * Returns an observable that combines the emissions of all [sources]. The downstream
     * emission is the result of applying the [combiner] function to the latest emissions
     * of all sources - and are only possible once all [sources] have emitted at least one
     * value.
     */
    @JvmStatic
    fun <T, R> combine(
        vararg sources: Observable<T>,
        combiner: (Iterable<T>) -> R
    ): Observable<R> {
        return combine(sources.asIterable(), combiner)
    }

    /**
     * Returns an observable that emits only when the provided [block] of code has completed,
     * This block will be executed whenever a new subscription is made.
     *
     * @param block The block of code to receive the observer with which to emit downstream.
     */
    @JvmStatic
    fun <T> callback(
        block: (Observer<T>) -> Unit
    ): Observable<T> {
        return CallbackObservable { observer ->
            val subscription = Subscription()
            block {
                if (subscription.isDisposed) return@block
                observer.onNext(it)
            }
            subscription
        }
    }

    /**
     * Returns an observable that emits only when the provided [block] of code has completed,
     * This block will be executed whenever a new subscription is made.
     *
     * @param block The block of code to receive the observer with which to emit downstream.
     */
    @JvmStatic
    fun <T> async(
        block: (Observer<T>) -> Disposable
    ): Observable<T> {
        return CallbackObservable(block)
    }
}