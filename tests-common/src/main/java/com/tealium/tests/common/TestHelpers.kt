package com.tealium.tests.common

import com.tealium.core.api.Tealium
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.Schedulers
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.internal.TealiumImpl
import com.tealium.core.internal.TealiumProxy
import com.tealium.core.internal.misc.SchedulersImpl
import com.tealium.core.internal.misc.SingleThreadedScheduler
import com.tealium.core.internal.misc.ThreadPoolScheduler

val testMainScheduler = SingleThreadedScheduler("test-main")
val testTealiumScheduler = SingleThreadedScheduler("tealium-test")
val testNetworkScheduler = ThreadPoolScheduler(0)
val testSchedulers: Schedulers = SchedulersImpl(testMainScheduler, testTealiumScheduler, testNetworkScheduler)

/**
 * Convenience method that awaits the execution of the [onReady] callback before returning it.
 */
fun createTealiumProxy(
    config: TealiumConfig,
    tealiumScheduler: Scheduler = testTealiumScheduler,
    onTealiumImplReady: Observable<TealiumResult<TealiumImpl>>,
    onShutdown: (String) -> Unit
): Tealium {
        return TealiumProxy(
            config.key,
            tealiumScheduler,
            onTealiumImplReady,
            onShutdown
        )
}