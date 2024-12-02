package com.tealium.core.internal.pubsub

import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.pubsub.CompositeDisposable
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.internal.pubsub.CompletedDisposable.dispose
import com.tealium.core.internal.pubsub.CompletedDisposable.isDisposed
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Base implementation of a [Disposable] that maintains the current disposed state.
 * Inheritors can use `super.dispose()` to mark the object as disposed.
 */
abstract class BaseDisposable : Disposable {

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
    private val disposables: MutableList<Disposable> = mutableListOf()
) : BaseDisposable(), CompositeDisposable {
    constructor() : this(mutableListOf())

    override fun dispose() {
        if (isDisposed) return
        super.dispose()

        val old = disposables.toList()
        disposables.clear()
        old.forEach {
            it.dispose()
        }
    }

    override fun add(disposable: Disposable) {
        if (disposable.isDisposed) return

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
    private var onDispose: (() -> Unit)? = null
) : BaseDisposable(), Disposable {

    override fun dispose() {
        if (isDisposed) return
        super.dispose()

        onDispose?.invoke()
        onDispose = null
    }
}

/**
 * Asynchronous [CompositeDisposable] implementation which supports adding/remove/disposing on a
 * specific [Scheduler].
 */
class AsyncDisposableContainer internal constructor(
    private val disposeOn: Scheduler,
    private val container: CompositeDisposable = DisposableContainer(),
) : CompositeDisposable {

    internal constructor(
        disposeOn: Scheduler,
        disposables: MutableList<Disposable> = mutableListOf()
    ) : this(disposeOn, DisposableContainer(disposables))

    constructor(
        disposeOn: Scheduler,
    ) : this(disposeOn, DisposableContainer())

    private val _disposed = AtomicBoolean(false)

    override val isDisposed: Boolean
        get() = _disposed.get()

    override fun dispose() {
        if (_disposed.compareAndSet(false, true)) {
            disposeOn.execute {
                container.dispose()
            }
        }
    }

    override fun add(disposable: Disposable) {
        disposeOn.execute {
            container.add(disposable)
        }
    }
}

/**
 * Simple [Disposable] implementation that wraps a [Runnable]. Disposal will cancel future execution
 * of the wrapped [Runnable] but it will not interrupt it if currently executing or has already
 * finished executing.
 */
class DisposableRunnable(
    private val wrappedRunnable: Runnable
    //TODO - consider possible callback to also remove from executor/handler queue
) : BaseDisposable(), Runnable {

    override fun run() {
        if (isDisposed) return

        wrappedRunnable.run()
    }
}

/**
 * A constant [Disposable] implementation which can be used when when no work is actually required
 * to execute upon disposing.
 *
 * [isDisposed] is always `true` and [dispose] does executes nothing.
 */
object CompletedDisposable : Disposable {
    override val isDisposed: Boolean = true
    override fun dispose() {}
}