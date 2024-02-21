package com.tealium.core.internal.observables

import com.tealium.core.api.listeners.Disposable
import java.util.concurrent.ExecutorService


/**
 * Defines a [Disposable] implementation that can dispose of multiple other [Disposable]
 * implementations in one go.
 */
interface CompositeDisposable : Disposable {
    /**
     * Adds the given [disposable] to be disposed of later.
     */
    fun add(disposable: Disposable)
}

/**
 * Base implementation of a [Disposable] that maintains the current disposed state.
 * Inheritors can use `super.dispose()` to mark the object as disposed.
 */
abstract class BaseDisposable: Disposable {

    @Volatile
    protected var _disposed = false

    override val isDisposed: Boolean
        get() = _disposed

    override fun dispose() {
        _disposed = true
    }
}

/**
 * The default implementation of [CompositeDisposable] to allow multiple [disposables] to be
 * disposed of at once.
 */
class DisposableContainer(
    private val disposables: MutableList<Disposable>
) : BaseDisposable(), CompositeDisposable {
    constructor() : this(mutableListOf())

    override fun dispose() {
        super.dispose()

        val old = disposables.toList()
        disposables.clear()
        old.forEach {
            it.dispose()
        }
    }

    override fun add(disposable: Disposable) {
        if (isDisposed) {
            disposable.dispose()
            return
        }

        disposables.add(disposable)
    }
}

/**
 * Default implementation of a [Disposable] that will call the provided [onDispose] function when
 * this is disposed of - useful for clearing up references.
 */
class Subscription(
    private val onDispose: (() -> Unit)? = null
): BaseDisposable(), Disposable {

    override fun dispose() {
        if (isDisposed) return

        onDispose?.invoke()
        super.dispose()
    }
}

/**
 * Implementation of [Disposable] that disposes of the subscription on the given [ExecutorService]
 */
class AsyncSubscription(
    private val disposeOn: ExecutorService,
    var subscription: Disposable? = null
): BaseDisposable(), Disposable {

    override fun dispose() {
        if (isDisposed) return

        disposeOn.submit {
            subscription?.dispose()
        }
        super.dispose()
    }
}