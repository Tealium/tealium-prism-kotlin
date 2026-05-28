package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Consumer
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.Subscribable


/**
 * Wrapper [Observer] implementation where both `onNext` and `onComplete` are guarded by the incoming
 * [disposable].
 *
 * Emissions only travel downstream if `this` is not yet disposed/completed
 *
 * This implementation will dispose on completion, so whilst it may be appropriate for many simple
 * [Observer] implementations, it will not be appropriate for others.
 */
class DisposableObserver<T>(
    private val observer: Observer<T>,
    private val disposable: UpstreamLinkable = UpstreamLinkableImpl()
) : LinkableObserver<T>, UpstreamLinkable by disposable {

    private var completed = false

    override fun onNext(value: T) {
        if (completed || isDisposed) return

        observer.onNext(value)
    }

    override fun onComplete() {
        if (completed || isDisposed) return
        completed = true

        observer.onComplete()
        dispose()
    }
}

/**
 * A lambda based [Observer] implementation used at the public subscription boundary [Subscribable.subscribe]
 *
 * Thread-safe because `subscribe` is typically called from an unknown thread while upstream emissions
 * and completion arrive on a different queue (e.g. via `subscribeOn`), creating a race between
 * [setUpstream] and [onComplete]/[dispose]
 *
 * This is also used by some intermediate operators that don't necessarily require thread-safety,
 * but it's simpler not to maintain an additional implementation.
 */
class AnonymousObserver<T> internal constructor(
    handleNext: Consumer<T>,
    handleComplete: Runnable
) : LinkableObserver<T> {

    constructor(handleNext: Consumer<T>) : this(handleNext, {})

    private var handleNext: Consumer<T>? = handleNext
    private var handleComplete: Runnable? = handleComplete
    private var upstream: Disposable? = null

    @Volatile
    private var _isDisposed: Boolean = false
    private val lock = Any()

    private fun stop() {
        handleNext = null
        handleComplete = null
    }

    override val isDisposed: Boolean
        get() = _isDisposed

    override fun onNext(value: T) {
        if (isDisposed) return

        val handleNext = synchronized(lock) {
            if (isDisposed) return@synchronized null

            handleNext
        }

        handleNext?.accept(value)
    }

    override fun onComplete() {
        if (isDisposed) return

        val handleComplete = synchronized(lock) {
            if (isDisposed) return@synchronized null

            handleComplete.also {
                stop()
            }
        }

        handleComplete?.run()
        dispose()
    }

    override fun dispose() {
        if (isDisposed) return

        val upstream = synchronized(lock) {
            if (isDisposed) return@synchronized null

            _isDisposed = true
            stop()
            upstream.also {
                upstream = null
            }
        }
        upstream?.dispose()
    }

    override fun setUpstream(upstream: Disposable) {
        val shouldDispose = synchronized(lock) {
            if (isDisposed) {
                return@synchronized true
            }

            this.upstream = upstream
            false
        }
        if (shouldDispose) {
            upstream.dispose()
        }
    }
}

/**
 * An [Observer] implementation that self-removes from the given [owner] upon completion. It delegates
 * other [Observer] behaviour to the given [delegate]
 */
class UnsubscribingObserver<T>(
    private val owner: CompositeDisposable,
    private val delegate: Observer<T>,
    private val disposable: UpstreamLinkable = UpstreamLinkableImpl()
) : Observer<T>, UpstreamLinkable by disposable {

    private var completed = false

    override fun onNext(value: T) {
        if (completed || isDisposed) return

        delegate.onNext(value)
    }

    override fun onComplete() {
        if (completed || isDisposed) return
        completed = true

        owner.remove(this)
        delegate.onComplete()
        dispose()
    }

    override fun dispose() {
        owner.remove(this)
        disposable.dispose()
    }
}
