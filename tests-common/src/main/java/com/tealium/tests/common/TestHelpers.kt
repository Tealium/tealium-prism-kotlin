package com.tealium.tests.common

import com.tealium.core.api.Tealium
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.misc.ActivityManager
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.internal.TealiumProxy
import com.tealium.core.internal.misc.ActivityManagerImpl
import com.tealium.core.internal.misc.SingleThreadedScheduler
import com.tealium.core.internal.misc.ThreadPoolScheduler
import com.tealium.core.internal.persistence.DatabaseProvider

val testTealiumScheduler = SingleThreadedScheduler("tealium-test")
val testNetworkScheduler = ThreadPoolScheduler(0)

/**
 * Convenience method that awaits the execution of the [onReady] callback before returning it.
 */
fun createTealiumProxy(
    config: TealiumConfig,
    databaseProvider: DatabaseProvider?,
    tealiumScheduler: Scheduler = testTealiumScheduler,
    networkSchedulerSupplier: () -> Scheduler = { testNetworkScheduler },
    activityManager: ActivityManager = ActivityManagerImpl(config.application),
    onReady: TealiumCallback<TealiumResult<Tealium>> = TealiumCallback { }
): Tealium {
    return if (databaseProvider != null) {
        TealiumProxy(
            config,
            onReady,
            tealiumScheduler,
            networkSchedulerSupplier,
            activityManager = activityManager,
            databaseProvider = databaseProvider
        )
    } else {
        TealiumProxy(
            config,
            onReady,
            tealiumScheduler,
            networkSchedulerSupplier,
            activityManager = activityManager
        )
    }
}