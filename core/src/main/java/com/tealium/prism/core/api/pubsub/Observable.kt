package com.tealium.prism.core.api.pubsub

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.internal.pubsub.AnonymousObserver
import com.tealium.prism.core.internal.pubsub.SingleImpl
import com.tealium.prism.core.internal.pubsub.impl.DistinctObservable
import com.tealium.prism.core.internal.pubsub.impl.FilterObservable
import com.tealium.prism.core.internal.pubsub.impl.FlatMapLatestObservable
import com.tealium.prism.core.internal.pubsub.impl.FlatMapObservable
import com.tealium.prism.core.internal.pubsub.impl.MapNotNullObservable
import com.tealium.prism.core.internal.pubsub.impl.MapObservable
import com.tealium.prism.core.internal.pubsub.impl.ObserveOnObservable
import com.tealium.prism.core.internal.pubsub.impl.ResubscribingObservable
import com.tealium.prism.core.internal.pubsub.impl.StartWithObservable
import com.tealium.prism.core.internal.pubsub.impl.SubscribeOnObservable
import com.tealium.prism.core.internal.pubsub.impl.TakeObservable
import com.tealium.prism.core.internal.pubsub.impl.TakeWhileObservable
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
     * Returns an observable that emits downstream up until the [predicate] returns false.
     */
    fun takeWhile(inclusive: Boolean, predicate: (T) -> Boolean): Observable<T> {
        return TakeWhileObservable(this, predicate, inclusive)
    }

    /**
     * Returns an observable that emits only the specified number of events given by the provided [count]
     *
     * @param count The number of emissions to emit downstream; must be a positive integer
     * @throws IllegalArgumentException when [count] is less than or equal to zero
     */
    fun take(count: Int): Observable<T> {
        return TakeObservable(this, count)
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
     * Ensures that the subscription to the source observable happens on the provided [scheduler].
     *
     * The source **must** emit on this same [scheduler] (see the first warning below); this operator
     * moves only the *subscription*, not the emission thread. It is useful to consume (via
     * [observeOn]) an [Observable] that emits from a thread different from the consumer's.
     *
     * Example:
     *
     * ```kotlin
     * // sourceScheduler: the scheduler the subject emits on
     * // consumerScheduler: the scheduler the consumer wants its values on
     * val subject = Observables.publishSubject<Int>()
     *
     * consumerScheduler.execute {
     *     subject.asObservable()
     *         .subscribeOn(sourceScheduler)   // subscribe to `subject` on the scheduler it emits from
     *         .observeOn(consumerScheduler)   // hop the emissions over to the consumer
     *         .take(1)
     *         .map { it * 10 }
     *         .subscribe { println(it) }      // must run on consumerScheduler, see the second warning
     * }
     *
     * sourceScheduler.execute {
     *     subject.onNext(1)
     * }
     * ```
     *
     * **Warning.** This method is intended for observables that emit from the same scheduler as the
     * one provided here. Calling this method on an [Observable] that emits from a different scheduler
     * will cause race conditions.
     *
     * You can [subscribe] to this [Observable] directly and you will receive `onNext` and `onComplete`
     * from the source scheduler (which must be the same as the provided scheduler) and you can dispose
     * from any thread.
     *
     * **Warning.** You can't chain any operator to the returned [Observable] other than [observeOn]
     * with the scheduler on which the consumer runs. After doing that you can chain any other
     * operator, as long as it also works from that same scheduler.
     */
    fun subscribeOn(scheduler: Scheduler): Observable<T> {
        return SubscribeOnObservable(this, scheduler)
    }

    /**
     * Ensures that downstream observers receive events on the provided [scheduler].
     *
     * This is useful to consume an [Observable] that emits from a thread different from the
     * consumer's: the source keeps emitting on its own thread, while `onNext`/`onComplete` are
     * re-delivered downstream on this [scheduler].
     *
     * Example:
     *
     * ```kotlin
     * // sourceScheduler: the scheduler the subject emits on
     * // consumerScheduler: the scheduler the consumer wants its values on
     * val subject = Observables.publishSubject<Int>()
     *
     * consumerScheduler.execute {
     *     subject.asObservable()
     *         .subscribeOn(sourceScheduler)   // subscribe to `subject` on the scheduler it emits from
     *         .observeOn(consumerScheduler)   // hop the emissions over to the consumer
     *         .take(1)
     *         .map { it * 10 }
     *         .subscribe { println(it) }
     * }
     *
     * sourceScheduler.execute {
     *     subject.onNext(1)
     * }
     * ```
     *
     * **Warning.** Disposing of a subscription to the returned [Observable] must happen from the same
     * [scheduler]. Disposing from a different scheduler races with event delivery on this [scheduler].
     *
     * **Warning.** Immediately before [observeOn] you must [subscribeOn] the producer scheduler, and
     * you must not chain any operator onto that [subscribeOn] before calling [observeOn]. See
     * [subscribeOn] for the full contract.
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
     * Returns an observable that combines the emissions of this observable the given others. The
     * downstream emission is the result of applying the [combiner] function to the latest emissions
     * of both observables - and are only possible once all others have emitted at least one value.
     */
    fun <T2, T3, R> combine(other1: Observable<T2>, other2: Observable<T3>, combiner: (T, T2, T3) -> R): Observable<R> {
        return Observables.combine(this, other1) { a, b -> a to b}
            .combine(other2) { (a, b), c -> combiner(a, b, c) }
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
     * the consumer with which to emit values downstream.
     */
    fun <R> async(block: (T, Consumer<R>) -> Disposable): Observable<R> {
        return flatMap { value ->
            Observables.async { onNext ->
                block(value, onNext)
            }
        }
    }

    /**
     * Returns an observable that will emit values in a possibly asynchronous manner determined by
     * the given [block].
     *
     * @param block a block of code, to be executed with the next value from the source, along with
     * the consumer with which to emit values downstream.
     */
    fun <R> callback(block: BiConsumer<T, Consumer<R>>): Observable<R> {
        return flatMap { value ->
            Observables.callback { onNext ->
                block.accept(value, onNext)
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
     * Converts this [Observable] to a [Single] subscribing on the given [Scheduler]
     *
     * A [take] and [subscribeOn] are applied to the source [Observable] automatically to
     * enforce only a single emission.
     */
    fun asSingle(scheduler: Scheduler): Single<T> {
        return SingleImpl(this, scheduler)
    }

    /**
     * Subscribes the given [observer] to a single emission of the source.
     */
    fun subscribeOnce(observer: Observer<T>): Disposable {
        return take(1)
            .subscribe(observer)
    }

    /**
     * Subscribes the given [onNext] to a single emission of the source.
     */
    fun subscribeOnce(onNext: Consumer<T>): Disposable =
        subscribeOnce(AnonymousObserver(onNext))
}