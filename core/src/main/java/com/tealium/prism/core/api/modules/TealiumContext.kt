package com.tealium.prism.core.api.modules

import android.content.Context
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.barriers.BarrierRegistry
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.misc.QueueMetrics
import com.tealium.prism.core.api.misc.Schedulers
import com.tealium.prism.core.api.network.NetworkUtilities
import com.tealium.prism.core.api.persistence.ModuleStoreProvider
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.session.SessionRegistry
import com.tealium.prism.core.api.settings.CoreSettings
import com.tealium.prism.core.api.tracking.Tracker
import com.tealium.prism.core.api.transform.TransformerRegistry

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
    val queueMetrics: QueueMetrics,
    val sessionRegistry: SessionRegistry
)
