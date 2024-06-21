package com.tealium.core

import android.content.Context
import com.tealium.core.api.ActivityManager
import com.tealium.core.api.ModuleManager
import com.tealium.core.api.ModuleStoreProvider
import com.tealium.core.api.Schedulers
import com.tealium.core.api.Tracker
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
    val visitorId: ObservableState<String>,
    val storageProvider: ModuleStoreProvider,
    val network: NetworkUtilities,
    val coreSettings: ObservableState<CoreSettings>,
    val tracker: Tracker,
    val schedulers: Schedulers,
    val activityManager: ActivityManager,
    val transformerRegistry: TransformerRegistry,
    val barrierRegistry: BarrierRegistry,
    val moduleManager: ModuleManager
)
