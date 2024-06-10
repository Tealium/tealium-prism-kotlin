package com.tealium.tests.common

import com.tealium.core.api.Scheduler
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.Subscription
import com.tealium.core.internal.persistence.TimeFrame

/**
 * Scheduler that executes the given [Runnable] in a synchronous manner in the caller thread.
 * All methods run the provided [Runnable]s this way - even if they are scheduled for a later date.
 */
class SynchronousScheduler: Scheduler {
    override fun execute(runnable: Runnable) {
        runnable.run()
    }

    override fun schedule(runnable: Runnable): Disposable {
        runnable.run()
        return Subscription()
    }

    override fun schedule(delay: TimeFrame, runnable: Runnable): Disposable {
        runnable.run()
        return Subscription()
    }
}