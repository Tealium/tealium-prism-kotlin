package com.tealium.core.internal.modules

import android.net.Uri
import com.tealium.core.BuildConfig
import com.tealium.core.TealiumConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumList
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.api.listeners.TealiumCallback
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.internal.LogCategory
import com.tealium.core.internal.observables.CompletedDisposable
import com.tealium.core.internal.observables.DisposableContainer
import com.tealium.core.internal.observables.addTo

/**
 * The [CollectDispatcher]
 */
class CollectDispatcher(
    private val config: TealiumConfig,
    private val logger: Logger,
    private val networkHelper: NetworkHelper,
    private var collectDispatcherSettings: CollectDispatcherSettings,
) : Dispatcher, Module {

    constructor(
        tealiumContext: TealiumContext,
        collectDispatcherSettings: CollectDispatcherSettings
    ) : this(
        tealiumContext.config,
        tealiumContext.logger,
        tealiumContext.network.networkHelper,
        collectDispatcherSettings
    )

    override val name: String
        get() = moduleName
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    override val dispatchLimit: Int
        get() = CollectDispatcherSettings.MAX_BATCH_SIZE

    override fun dispatch(
        dispatches: List<Dispatch>,
        callback: TealiumCallback<List<Dispatch>>
    ): Disposable {
        return if (dispatches.size == 1) {
            sendSingle(dispatches.first(), callback)
        } else {
            sendBatch(dispatches, callback)
        }
    }

    private fun sendBatch(
        dispatches: List<Dispatch>,
        onProcessed: TealiumCallback<List<Dispatch>>
    ): Disposable {
        val disposableContainer = DisposableContainer()

        val groupedDispatches = dispatches.groupBy {
            it.payload().getString(Dispatch.Keys.TEALIUM_VISITOR_ID)
        }
        logger.trace?.log(
            LogCategory.COLLECT, "Collect events split in batches ${
                groupedDispatches.mapValues { it.value.map(Dispatch::logDescription) }
            }"
        )

        groupedDispatches.mapNotNull { (visitorId, dispatches) ->
            if (visitorId == null) { // shouldn't happen
                onProcessed.onComplete(dispatches)
                CompletedDisposable
            } else {
                sendBatch(visitorId, dispatches, onProcessed)
            }.addTo(disposableContainer)
        }

        return disposableContainer
    }

    private fun sendBatch(
        visitorId: String,
        batch: List<Dispatch>,
        onProcessed: TealiumCallback<List<Dispatch>>
    ): Disposable {
        if (batch.count() == 1) {
            return sendSingle(batch.first(), onProcessed)
        }

        val compressed = compressDispatches(
            batch,
            visitorId,
            config.accountName,
            collectDispatcherSettings.profile ?: config.profileName
        )
        if (compressed == null) {
            onProcessed.onComplete(batch)
            return CompletedDisposable
        }

        return networkHelper.post(collectDispatcherSettings.batchUrl, compressed) {
            onProcessed.onComplete(batch)
        }
    }

    private fun sendSingle(
        dispatch: Dispatch,
        onProcessed: TealiumCallback<List<Dispatch>>
    ): Disposable {
        collectDispatcherSettings.profile?.let { profile ->
            dispatch.addAll(TealiumBundle.create {
                put(Dispatch.Keys.TEALIUM_PROFILE, profile)
            })
        }

        return networkHelper.post(collectDispatcherSettings.url, dispatch.payload()) {
            onProcessed.onComplete(listOf(dispatch))
        }
    }

    override fun updateSettings(moduleSettings: ModuleSettings): Module? {
        if (!moduleSettings.enabled) return null

        collectDispatcherSettings = CollectDispatcherSettings.fromModuleSettings(moduleSettings)
        return this
    }

    companion object {
        const val moduleName = "CollectDispatcher"

        const val KEY_SHARED = "shared"
        const val KEY_EVENTS = "events"

        fun compressDispatches(
            dispatches: List<Dispatch>,
            visitorId: String,
            account: String,
            profile: String
        ): TealiumBundle? {
            return compressBundles(dispatches.map { dispatch ->
                dispatch.payload()
            }, visitorId, account, profile)
        }

        /**
         * Compresses a collection of TealiumBundles into a new JSON format where known keys are
         * in a "shared" sub key, and the events have common data removed and placed in an "events" sub key.
         *
         * The [bundles] are expected to contain events associated to only a single Visitor Id.
         *
         * The end result is a JSON of the following format:
         * ```json
         * {
         *   "shared": {
         *      ...
         *   },
         *   "events": [{
         *      ...
         *   },{
         *      ...
         *   }]
         * }
         * ```
         */
        fun compressBundles(
            bundles: List<TealiumBundle>,
            visitorId: String,
            account: String,
            profile: String
        ): TealiumBundle? {
            if (bundles.isEmpty()) return null

            val compressed = TealiumBundle.Builder()
            val shared = TealiumBundle.create {
                put(Dispatch.Keys.TEALIUM_ACCOUNT, account)
                put(Dispatch.Keys.TEALIUM_PROFILE, profile)
                put(Dispatch.Keys.TEALIUM_VISITOR_ID, visitorId)
            }

            val events = TealiumList.create {
                bundles.forEach { bundle ->
                    add(bundle.copy {
                        remove(Dispatch.Keys.TEALIUM_ACCOUNT)
                        remove(Dispatch.Keys.TEALIUM_PROFILE)
                        remove(Dispatch.Keys.TEALIUM_VISITOR_ID)
                    })
                }
            }

            return compressed.put(KEY_SHARED, shared)
                .put(KEY_EVENTS, events)
                .getBundle()
        }
    }

    object Factory : ModuleFactory {
        override val name: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: ModuleSettings): Module? {
            if (!settings.enabled) return null

            return CollectDispatcher(
                context,
                CollectDispatcherSettings.fromModuleSettings(settings)
            )
        }
    }
}

/**
 * Carries all available settings for the Collect Dispatcher
 *
 * @param url The endpoint to dispatch single events to
 * @param batchUrl The endpoint to dispatch batched events to
 * @param profile Optional - Tealium profile name to override on the payload
 */
data class CollectDispatcherSettings(
    val enabled: Boolean = true,
    val dependencies: List<Any> = emptyList(),
    var url: String = DEFAULT_COLLECT_URL,
    var batchUrl: String = DEFAULT_COLLECT_BATCH_URL,
    var profile: String? = null
) {

    companion object {
        const val MAX_BATCH_SIZE = 10
        const val DEFAULT_COLLECT_URL = "https://collect.tealiumiq.com/event"
        const val DEFAULT_COLLECT_BATCH_URL = "https://collect.tealiumiq.com/bulk-event"

        /**
         * Current module settings from Mobile Data Source front-end.
         * TODO - Validate that these are final
         *
         * "collect": {
         *   "enabled": true,
         *   "dispatch_profile": "...",
         *   "single_dispatch_url": "https://collect.tealiumiq.com/event/",
         *   "batch_dispatch_url": "https://collect.tealiumiq.com/bulk-event/",
         *   "dispatch_domain": "collect.tealiumiq.com"
         * }
         */
        const val KEY_COLLECT_DOMAIN = "dispatch_domain"
        const val KEY_COLLECT_URL = "single_dispatch_url"
        const val KEY_COLLECT_BATCH_URL = "batch_dispatch_url"
        const val KEY_COLLECT_PROFILE = "dispatch_profile"

        private fun configureDomain(url: String, domain: String?): String? {
            return if (domain == null) null else
                Uri.parse(url).buildUpon()
                    .authority(domain)
                    .build()
                    .toString()
        }

        fun fromModuleSettings(settings: ModuleSettings): CollectDispatcherSettings {
            val dependencies = settings.dependencies
            val domain = settings.bundle.getString(KEY_COLLECT_DOMAIN)

            val url = settings.bundle.getString(KEY_COLLECT_URL)
                ?: configureDomain(DEFAULT_COLLECT_URL, domain)
                ?: DEFAULT_COLLECT_URL
            val batchUrl = settings.bundle.getString(KEY_COLLECT_BATCH_URL)
                ?: configureDomain(DEFAULT_COLLECT_BATCH_URL, domain)
                ?: DEFAULT_COLLECT_BATCH_URL
            val profile = settings.bundle.getString(KEY_COLLECT_PROFILE)

            return CollectDispatcherSettings(
                url = url, batchUrl = batchUrl, profile = profile, dependencies = dependencies
            )
        }
    }
}
