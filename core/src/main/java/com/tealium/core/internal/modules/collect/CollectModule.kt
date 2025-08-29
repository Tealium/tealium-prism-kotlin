package com.tealium.core.internal.modules.collect

import com.tealium.core.BuildConfig
import com.tealium.core.api.Modules
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.settings.CollectSettingsBuilder
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.internal.logger.LogCategory
import com.tealium.core.api.logger.logIfTraceEnabled
import com.tealium.core.internal.logger.logDescriptions
import com.tealium.core.internal.pubsub.CompletedDisposable
import com.tealium.core.internal.pubsub.DisposableContainer
import com.tealium.core.internal.pubsub.addTo

/**
 * The [CollectModule]
 */
class CollectModule(
    private val config: TealiumConfig,
    private val logger: Logger,
    private val networkHelper: NetworkHelper,
    private var collectModuleConfiguration: CollectModuleConfiguration,
) : Dispatcher, Module {

    constructor(
        tealiumContext: TealiumContext,
        collectModuleConfiguration: CollectModuleConfiguration
    ) : this(
        tealiumContext.config,
        tealiumContext.logger,
        tealiumContext.network.networkHelper,
        collectModuleConfiguration
    )

    override val id: String = Modules.Ids.COLLECT
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    override val dispatchLimit: Int
        get() = CollectModuleConfiguration.MAX_BATCH_SIZE

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
        logger.logIfTraceEnabled(LogCategory.COLLECT) {
            "Collect events split in batches ${
                groupedDispatches.mapValues { it.value.logDescriptions() }
            }"
        }

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
            collectModuleConfiguration.profile ?: config.profileName
        )
        if (compressed == null) {
            onProcessed.onComplete(batch)
            return CompletedDisposable
        }

        return networkHelper.post(collectModuleConfiguration.batchUrl, compressed) {
            onProcessed.onComplete(batch)
        }
    }

    private fun sendSingle(
        dispatch: Dispatch,
        onProcessed: TealiumCallback<List<Dispatch>>
    ): Disposable {
        collectModuleConfiguration.profile?.let { profile ->
            dispatch.addAll(DataObject.create {
                put(Dispatch.Keys.TEALIUM_PROFILE, profile)
            })
        }

        return networkHelper.post(collectModuleConfiguration.url, dispatch.payload()) {
            onProcessed.onComplete(listOf(dispatch))
        }
    }

    override fun updateConfiguration(configuration: DataObject): Module? {
        val newConfiguration = CollectModuleConfiguration.fromDataObject(configuration) ?: return null

        collectModuleConfiguration = newConfiguration
        return this
    }

    companion object {
        const val KEY_SHARED = "shared"
        const val KEY_EVENTS = "events"

        fun compressDispatches(
            dispatches: List<Dispatch>,
            visitorId: String,
            account: String,
            profile: String
        ): DataObject? {
            return compressDataObjects(dispatches.map { dispatch ->
                dispatch.payload()
            }, visitorId, account, profile)
        }

        /**
         * Compresses a collection of [DataObject]s into a new JSON format where known keys are
         * in a "shared" sub key, and the events have common data removed and placed in an "events" sub key.
         *
         * The [dataObjects] are expected to contain events associated to only a single Visitor Id.
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
        fun compressDataObjects(
            dataObjects: List<DataObject>,
            visitorId: String,
            account: String,
            profile: String
        ): DataObject? {
            if (dataObjects.isEmpty()) return null

            val compressed = DataObject.Builder()
            val shared = DataObject.create {
                put(Dispatch.Keys.TEALIUM_ACCOUNT, account)
                put(Dispatch.Keys.TEALIUM_PROFILE, profile)
                put(Dispatch.Keys.TEALIUM_VISITOR_ID, visitorId)
            }

            val events = DataList.create {
                dataObjects.forEach { dataObject ->
                    add(dataObject.copy {
                        remove(Dispatch.Keys.TEALIUM_ACCOUNT)
                        remove(Dispatch.Keys.TEALIUM_PROFILE)
                        remove(Dispatch.Keys.TEALIUM_VISITOR_ID)
                    })
                }
            }

            return compressed.put(KEY_SHARED, shared)
                .put(KEY_EVENTS, events)
                .build()
        }

    }

    class Factory(
        private val settings: DataObject? = null
    ) : ModuleFactory {

        constructor(builder: CollectSettingsBuilder) : this(builder.build())

        override val id: String = Modules.Ids.COLLECT

        override fun getEnforcedSettings(): DataObject? = settings

        override fun create(context: TealiumContext, configuration: DataObject): Module? {
            val collectModuleConfiguration =
                CollectModuleConfiguration.fromDataObject(configuration) ?: return null

            return CollectModule(
                context,
                collectModuleConfiguration
            )
        }
    }
}
