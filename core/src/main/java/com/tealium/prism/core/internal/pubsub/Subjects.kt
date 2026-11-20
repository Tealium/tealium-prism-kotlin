package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.ReplaySubject
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.pubsub.impl.ObservableStateImpl
import com.tealium.prism.core.internal.pubsub.impl.PassthroughObservable


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
 *
 * A subclass that registers a wrapper around the subscribed observer overrides [subscribe]
 * and passes the wrapper to `super.subscribe`.
 */
abstract class BaseSubjectImpl<T> : Subject<T> {

    protected val lock = Any()
    protected var isCompleted = false
        private set

    private val subscribers: MutableList<Observer<T>> = mutableListOf()

    override val count: Int
        get() = synchronized(lock) {
            subscribers.count()
        }

    override fun onNext(value: T) {
        synchronized(lock) {
            if (isCompleted) return

            onBeforeNext(value)

            // can't mutate while iterating
            val subscribersCopy = subscribers.toTypedArray()

            subscribersCopy.forEach { subscriber ->
                subscriber.onNext(value)
            }

            onAfterNext(value)
        }
    }

    /**
     * Runs [onBeforeSubscribed], registers [observer], then runs [onAfterSubscribed] with it,
     * under [lock]; a value emitted from [onAfterSubscribed], or from a callback it invokes,
     * reaches [observer].
     * On a completed subject [observer] is not registered and [onBeforeSubscribed] does not run:
     * [observer] receives [onAfterSubscribed], then the completion, and the returned Disposable
     * is already disposed.
     */
    override fun subscribe(observer: Observer<T>): Disposable {
        synchronized(lock) {
            if (isCompleted) {
                onAfterSubscribed(observer)
                observer.onComplete()
                return Disposables.disposed()
            }

            onBeforeSubscribed(observer)
            val subscribed = subscribers.add(observer)

            if (subscribed) {
                onAfterSubscribed(observer)
            }
        }
        return Subscription {
            remove(observer)
        }
    }

    override fun onComplete() {
        if (isCompleted) return

        synchronized(lock) {
            if (isCompleted) return

            isCompleted = true
            val subscribersCopy = subscribers.toTypedArray()
            subscribers.clear()

            subscribersCopy.forEach(Observer<T>::onComplete)
        }
    }

    override fun asObservable(): Observable<T> {
        return PassthroughObservable(this)
    }

    protected fun remove(observer: Observer<T>) {
        synchronized(lock) {
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
     * Executed under the lock right after [observer] is registered, or, on a completed subject,
     * before [observer] is completed without being registered. [observer] is the observer
     * passed to [subscribe], or the wrapper a subclass passed to `super.subscribe`.
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

    override fun subscribe(observer: Observer<T>): Disposable =
        super<BaseSubjectImpl>.subscribe(ReplayingObserver(observer))

    override fun onAfterSubscribed(observer: Observer<T>) {
        (observer as ReplayingObserver<T>).replay(listOf(value))
    }

    override fun asObservableState(): ObservableState<T> {
        return ObservableStateImpl(this)
    }
}

/**
 * Caches [cacheSize] number of items. Subscribers that subscribe after values
 * have already been emitted, will receive the last [cacheSize] values upon successful
 * subscription
 * */
class ReplaySubjectImpl<T>(
    cacheSize: Int
) : BaseSubjectImpl<T>(), ReplaySubject<T> {

    private val cache = ArrayDeque<T>()
    private var cacheSize = standardizeSize(cacheSize)

    override fun clear() {
        synchronized(lock) {
            cache.clear()
        }
    }

    override fun resize(size: Int) {
        synchronized(lock) {
            val newSize = standardizeSize(size)

            while (cache.size > newSize) {
                cache.removeFirst()
            }

            cacheSize = newSize
        }
    }

    override fun onBeforeNext(t: T) {
        if (cacheSize == 0) {
            return
        }

        if (cache.size >= cacheSize) {
            cache.removeFirst()
        }

        cache.addLast(t)
    }

    override fun subscribe(observer: Observer<T>): Disposable =
        super<BaseSubjectImpl>.subscribe(ReplayingObserver(observer))

    override fun onAfterSubscribed(observer: Observer<T>) {
        if (cache.isEmpty()) return

        // snapshot: the observer may emit into, clear or resize this subject while replaying
        (observer as ReplayingObserver<T>).replay(cache.toList())
    }

    override fun last(): T? =
        synchronized(lock) {
            cache.lastOrNull()
        }

    /**
     * Returns a non-negative value for the given [size].
     */
    private fun standardizeSize(size: Int): Int {
        return if (size < 0) Int.MAX_VALUE else size
    }
}

/**
 * Registered entry for a [downstream] observer of a [ReplaySubjectImpl] or [StateSubjectImpl].
 *
 * While [replay] runs, emissions from the subject are buffered for this entry only and
 * delivered after the replayed values; emissions made while delivering the buffer are
 * buffered too. Otherwise emissions are forwarded directly.
 *
 * Every member is accessed only while holding the subject's lock.
 */
private class ReplayingObserver<T>(
    private val downstream: Observer<T>
) : Observer<T> {

    private var isBuffering = false
    private val buffer = ArrayDeque<T>()
    private var completionBuffered = false

    override fun onNext(value: T) {
        if (isBuffering) {
            buffer.addLast(value)
        } else {
            downstream.onNext(value)
        }
    }

    override fun onComplete() {
        if (isBuffering) {
            completionBuffered = true
        } else {
            downstream.onComplete()
        }
    }

    /**
     * Delivers [values], then everything buffered meanwhile, then a buffered completion.
     *
     * If [downstream] throws, buffering stops and the exception propagates: the rest of the
     * replay and anything buffered, including a completion, are dropped, and later emissions
     * reach [downstream] directly.
     */
    fun replay(values: List<T>) {
        isBuffering = true
        try {
            values.forEach(downstream::onNext)
            while (buffer.isNotEmpty()) {
                downstream.onNext(buffer.removeFirst())
            }
        } finally {
            isBuffering = false
            buffer.clear()
        }
        if (completionBuffered) {
            completionBuffered = false
            downstream.onComplete()
        }
    }
}

/**
 * Simple subject that emits values only to existing subscribers.
 *
 * Subscribers receive no default or historically emitted values upon subscription
 */
class PublishSubject<T> : BaseSubjectImpl<T>()