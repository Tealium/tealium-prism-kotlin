package com.tealium.tests.common

import com.tealium.core.Tealium
import com.tealium.core.TealiumConfig
import com.tealium.core.internal.TealiumImpl
import com.tealium.core.internal.persistence.DatabaseProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ScheduledExecutorService
import kotlin.coroutines.resumeWithException

/**
 * Convenience method that awaits the execution of the [onReady] callback before returning it.
 */
suspend fun awaitCreateTealiumImpl(
    config: TealiumConfig,
    databaseProvider: DatabaseProvider? = null,
    executorService: ScheduledExecutorService? = null,
    onReady: (Tealium, Exception?) -> Unit = { _, _ -> },
): Tealium {
    return suspendCancellableCoroutine { cont ->
        val callback = Tealium.OnTealiumReady { tealium, error ->
            try {
                onReady.invoke(tealium, error)
                cont.resume(tealium, null)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }

        if (databaseProvider != null && executorService != null) {
            TealiumImpl(config, callback, databaseProvider, executorService)
        } else if (databaseProvider != null && executorService == null) {
            TealiumImpl(config, callback, dbProvider = databaseProvider)
        } else if (databaseProvider == null && executorService != null) {
            TealiumImpl(config, callback, backgroundService = executorService)
        } else {
            TealiumImpl(config, callback)
        }
    }
}