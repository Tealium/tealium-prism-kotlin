package com.tealium.core.internal.observables

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.observables.impl.ObservableStateImpl
import com.tealium.core.internal.observables.impl.PassthroughObservable
import java.util.*

/**
 * A [Subject] is an [Observable] that can be used to publish new values to any
 * subscribers, where an [Observable] cannot.
 *
 * New values can be emitted to subscribers via the [Observer.onNext] method.
 */
interface Subject<T>: Observable<T>, Observer<T> {

    /**
     * Returns the number of subscribers to this [Subject]
     */
    val count : Int

    /**
     * Returns this [Subject] as an [Observable] to restrict publishing.
     */
    fun asObservable() : Observable<T>
}

/**
 * A [StateSubject] is a specialized [Subject] that also maintains the latest emission
 * as state, retrievable via the [value] getter.
 *
 * New values can be emitted to subscribers via the [Observer.onNext] method.
 */
interface StateSubject<T>: Subject<T>, ObservableState<T> {

    /**
     * Returns the [StateSubject] as an [ObservableState] that therefore cannot be published to.
     */
    fun asObservableState() : ObservableState<T>
}

/**
 * A [ReplaySubject] is a specialized [Subject] that also maintains the latest N emissions
 * in a cache. Upon subscription, all values in the cache will be emitted to the subscriber.
 *
 * New values can be emitted to subscribers via the [Observer.onNext] method.
 */
interface ReplaySubject<T>: Subject<T> {
    fun clear()
}


/**
 * Base Subject implementation. Addition and removal of Subscribers is
 * done in a synchronized manner.
 *
 * Behaviorally, this base class acts as a PublishSubject - that is, it only
 * emits new values to existing observers by default - but there is a
 * non-extendable, concrete implementation for that use available here:
 * @see PublishSubject
 *
 * If the referent was not properly disposed, then it will still receive
 * emitted values.
 *
 * Although some of the member methods remain overrideable, such as [onNext],
 * when extending this class it would be advisable to use the [onBeforeNext],
 * [onAfterNext], [onBeforeSubscribed] and [onAfterSubscribed] which are all
 * executed within the synchronized access of the subscribers.
 */
abstract class BaseSubjectImpl<T> : Subject<T> {

    private val subscribers: MutableList<Observer<T>> = mutableListOf()

    override val count: Int
        get() = subscribers.count()

    override fun onNext(value: T) {
        synchronized(this) {
            onBeforeNext(value)

            // can't mutate while iterating
            val subscribersCopy = subscribers.toTypedArray()

            subscribersCopy.forEach { subscriber ->
                subscriber.onNext(value)
            }

            onAfterNext(value)
        }
    }

    final override fun subscribe(observer: Observer<T>): Disposable {
        synchronized(this) {
            onBeforeSubscribed(observer)
            val subscribed = subscribers.add(observer)

            if (subscribed) {
                onAfterSubscribed(observer)
            }
        }
        return Subscription {
            subscribers.remove(observer)
        }
    }

    override fun asObservable() : Observable<T> {
        return PassthroughObservable(this)
    }

    protected fun remove(observer: Observer<T>) {
        synchronized(this) {
            subscribers.remove(observer)
        }
    }

    /**
     * Executed before a new value has been emitted to subscribers
     */
    protected open fun onBeforeNext(t: T) {}

    /**
     * Executed after a new value has been emitted to subscribers
     */
    protected open fun onAfterNext(t: T) {}

    /**
     * Executed before any subscription attempt
     */
    protected open fun onBeforeSubscribed(observer: Observer<T>) {}

    /**
     * Executed after a successful subscription
     */
    protected open fun onAfterSubscribed(observer: Observer<T>) {}
}


/**
 * Subject that will automatically publish the last emitted value to a
 * new subscriber.
 *
 * A default value can be provided on creation, such that if no values have
 * been emitted when an observer subscribes, then they will receive the default
 * value.
 * If no default value is provided, and no values have been emitted, then
 * new subscribers will not receive a value until the next one is emitted.
 */
class StateSubjectImpl<T>(
    initialValue: T
) : BaseSubjectImpl<T>(), StateSubject<T> {

    @Volatile
    override var value: T = initialValue
        private set

    override fun onBeforeNext(t: T) {
        value = t
    }

    override fun onAfterSubscribed(observer: Observer<T>) {
        observer.onNext(value)
    }

    override fun asObservableState() : ObservableState<T> {
        return ObservableStateImpl(this)
    }
}

/**
 * Caches [cacheSize] number of items. Subscribers that subscribe after values
 * have already been emitted, will receive the last [cacheSize] values upon successful
 * subscription
 * */
class ReplaySubjectImpl<T>(
    private val cacheSize: Int,
    private val cache: Queue<T> = LinkedList()
) : BaseSubjectImpl<T>(), ReplaySubject<T> {

    override fun clear() {
        cache.clear()
    }

    override fun onBeforeNext(t: T) {
        if (cache.size >= cacheSize) {
            cache.poll()
        }

        cache.offer(t)
    }

    override fun onAfterSubscribed(observer: Observer<T>) {
        for (value in cache) {
            observer.onNext(value)
        }
    }
}

/**
 * Simple subject that emits values only to existing subscribers.
 *
 * Subscribers receive no default or historically emitted values upon subscription
 */
class PublishSubject<T> : BaseSubjectImpl<T>()