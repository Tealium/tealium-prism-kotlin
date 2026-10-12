package com.tealium.prism.core.api.modules

import android.content.Context
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.barriers.BarrierRegistrar
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.misc.QueueMetrics
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.Schedulers
import com.tealium.prism.core.api.network.NetworkUtilities
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.ModuleStoreProvider
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.session.Session
import com.tealium.prism.core.api.session.SessionRegistry
import com.tealium.prism.core.api.settings.CoreSettings
import com.tealium.prism.core.api.tracking.Tracker
import com.tealium.prism.core.api.transform.TransformerRegistrar

/**
 * Provides context and dependencies that might be required by [Module] implementations used in a
 * specific [Tealium] instance
 *
 * @property context The Android [Context]
 * @property config The [TealiumConfig] used to initialize the Tealium SDK instance
 * @property logger The [Logger] used by this [Tealium] instance
 * @property storageProvider A utility that can be used to fetch a [Module]'s [DataStore]
 * @property network A utility class for easily making asynchronous network requests
 * @property coreSettings An observable to subscribing to changes to the [CoreSettings]
 * @property tracker A [Tracker] to allow modules to track additional events
 * @property schedulers A set of common [Scheduler] implementations for consistent processing across the [Tealium] instance
 * @property activityManager A utility class to subscribe to application and activity status changes
 * @property transformerRegistrar A place to register additional transformations at runtime
 * @property barrierRegistrar A place to register additional barriers at runtime
 * @property moduleManager A utility for fetching other module implementations
 * @property queueMetrics A utility to allow receiving notifications of changes to the number of queued events
 * @property sessionRegistry A utility to allow subscribing to updates to the current [Session]
 */
class TealiumContext private constructor(
    val context: Context,
    val config: TealiumConfig,
    val logger: Logger,
    val storageProvider: ModuleStoreProvider,
    val network: NetworkUtilities,
    val coreSettings: ObservableState<CoreSettings>,
    val tracker: Tracker,
    val schedulers: Schedulers,
    val activityManager: ActivityManager,
    val transformerRegistrar: TransformerRegistrar,
    val barrierRegistrar: BarrierRegistrar,
    val moduleManager: ModuleManager,
    val queueMetrics: QueueMetrics,
    val sessionRegistry: SessionRegistry,
    val dataLayer: DataStore,
    /**
     * The modules of the last completed settings pass, seeded with an empty list. A synchronous read
     * cannot tell "no such module" from "not created yet", so read it only at collect or dispatch
     * time, when the tracker has already waited for the modules. For anything else use
     * [moduleManager].observeModule(s).
     */
    @get:JvmSynthetic internal val unsafeModules: ObservableState<List<Module>>
) {
    companion object {
        @JvmSynthetic
        internal fun create(
            context: Context,
            config: TealiumConfig,
            logger: Logger,
            storageProvider: ModuleStoreProvider,
            network: NetworkUtilities,
            coreSettings: ObservableState<CoreSettings>,
            tracker: Tracker,
            schedulers: Schedulers,
            activityManager: ActivityManager,
            transformerRegistrar: TransformerRegistrar,
            barrierRegistrar: BarrierRegistrar,
            moduleManager: ModuleManager,
            queueMetrics: QueueMetrics,
            sessionRegistry: SessionRegistry,
            dataLayer: DataStore,
            unsafeModules: ObservableState<List<Module>>
        ): TealiumContext {
            return TealiumContext(
                context = context,
                config = config,
                logger = logger,
                storageProvider = storageProvider,
                network = network,
                coreSettings = coreSettings,
                tracker = tracker,
                schedulers = schedulers,
                activityManager = activityManager,
                transformerRegistrar = transformerRegistrar,
                barrierRegistrar = barrierRegistrar,
                moduleManager = moduleManager,
                queueMetrics = queueMetrics,
                sessionRegistry = sessionRegistry,
                dataLayer = dataLayer,
                unsafeModules = unsafeModules
            )
        }
    }
}
