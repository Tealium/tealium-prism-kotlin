package com.tealium.core.internal.modules.collect

import com.tealium.core.BuildConfig
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumList
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.settings.CollectDispatcherSettingsBuilder
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.internal.logger.LogCategory
import com.tealium.core.internal.pubsub.CompletedDisposable
import com.tealium.core.internal.pubsub.DisposableContainer
import com.tealium.core.internal.pubsub.addTo

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

    override val id: String
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

    override fun updateSettings(moduleSettings: TealiumBundle): Module? {
        val newSettings = CollectDispatcherSettings.fromBundle(moduleSettings) ?: return null

        collectDispatcherSettings = newSettings
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

    class Factory(
        private val settings: TealiumBundle? = null
    ) : ModuleFactory {

        constructor(builder: CollectDispatcherSettingsBuilder) : this(builder.build())

        override val id: String
            get() = moduleName

        override fun getEnforcedSettings(): TealiumBundle? = settings

        override fun create(context: TealiumContext, settings: TealiumBundle): Module? {
            val collectDispatcherSettings = CollectDispatcherSettings.fromBundle(settings) ?: return null

            return CollectDispatcher(
                context,
                collectDispatcherSettings
            )
        }
    }
}
