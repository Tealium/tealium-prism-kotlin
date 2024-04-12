package com.tealium.tests.common

import com.tealium.core.Tealium
import com.tealium.core.TealiumConfig
import com.tealium.core.api.ActivityManager
import com.tealium.core.api.TealiumResult
import com.tealium.core.api.Scheduler
import com.tealium.core.api.listeners.TealiumCallback
import com.tealium.core.internal.ActivityManagerImpl
import com.tealium.core.internal.IoScheduler
import com.tealium.core.internal.TealiumProxy
import com.tealium.core.internal.TealiumScheduler
import com.tealium.core.internal.persistence.DatabaseProvider
import java.util.concurrent.Executors

private val tealiumExecutor = Executors.newSingleThreadScheduledExecutor()
private val networkExecutor = Executors.newCachedThreadPool()

val testTealiumScheduler = TealiumScheduler(tealiumExecutor)
val testNetworkScheduler = IoScheduler(networkExecutor, tealiumExecutor)

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