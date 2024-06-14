package com.tealium.core

import android.content.Context
import com.tealium.core.api.*
import com.tealium.core.api.barriers.BarrierRegistry
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.api.transformations.TransformerRegistry
import com.tealium.core.internal.observables.ObservableState
import com.tealium.core.internal.settings.CoreSettings

class TealiumContext(
    val context: Context,
    val config: TealiumConfig,
    val logger: Logger,
    // TODO - find a better place to access this?
    visitorId: String, // todo
    val storageProvider: ModuleStoreProvider,
    val network: NetworkUtilities,
    val coreSettings: ObservableState<CoreSettings>,
    val tracker: Tracker,
    val schedulers: Schedulers,
    val activityManager: ActivityManager,
    val transformerRegistry: TransformerRegistry,
    val barrierRegistry: BarrierRegistry,
    val moduleManager: ModuleManager
) {
    private val _visitorId = visitorId

    val visitorId: String
        get() {
            //TODO()
            //tealium.
            return _visitorId
        }
}
