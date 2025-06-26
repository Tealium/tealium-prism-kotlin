package com.tealium.core.api.modules

import android.content.Context
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.barriers.BarrierRegistry
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.misc.ActivityManager
import com.tealium.core.api.misc.Schedulers
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.api.persistence.ModuleStoreProvider
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.settings.CoreSettings
import com.tealium.core.api.tracking.Tracker
import com.tealium.core.api.transform.TransformerRegistry
import com.tealium.core.api.misc.QueueMetrics

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
    val moduleManager: ModuleManager,
    val queueMetrics: QueueMetrics
)
