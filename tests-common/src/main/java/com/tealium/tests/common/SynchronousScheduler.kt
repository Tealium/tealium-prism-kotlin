package com.tealium.tests.common

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.internal.pubsub.Subscription
import com.tealium.prism.core.api.misc.TimeFrame

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