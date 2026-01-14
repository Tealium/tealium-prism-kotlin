package com.tealium.prism.momentsapi.internal

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfErrorEnabled
import com.tealium.prism.core.api.logger.logIfTraceEnabled
import com.tealium.prism.core.api.logger.logIfWarnEnabled
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Single
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.momentsapi.BuildConfig
import com.tealium.prism.momentsapi.EngineResponse
import com.tealium.prism.momentsapi.MomentsApi
import com.tealium.prism.momentsapi.MomentsApiSettingsBuilder

class MomentsApiWrapper(
    private val moduleProxy: ModuleProxy<MomentsApiModule>
) : MomentsApi {
    constructor(tealium: Tealium) : this(
        tealium.createModuleProxy(MomentsApiModule::class.java)
    )

    override fun fetchEngineResponse(engineId: String): Single<TealiumResult<EngineResponse>> {
        return moduleProxy.executeAsyncModuleTask { module, callback ->
            module.fetchEngineResponse(engineId, callback)
        }
    }
}

/**
 * Internal MomentsApi module implementation.
 */
class MomentsApiModule(
    private val visitorId: ObservableState<String>,
    private val logger: Logger,
    private var service: MomentsApiService
) : Module {

    constructor(
        context: TealiumContext,
        configuration: MomentsApiConfiguration,
    ) : this(
        context.visitorId,
        context.logger,
        MomentsApiServiceImpl(
            networkHelper = context.network.networkHelper,
            account = context.config.accountName,
            profile = context.config.profileName,
            environment = context.config.environment,
            configuration = configuration
        )
    )

    private val disposables: CompositeDisposable = DisposableContainer()
    
    fun fetchEngineResponse(engineId: String, callback: Callback<TealiumResult<EngineResponse>>) {
        val visitorId = visitorId.value

        logger.logIfTraceEnabled(id) {
            "Fetching MomentsApi response for engine: $engineId, visitor: $visitorId"
        }
        
        service.fetchEngineResponse(engineId, visitorId) { result ->
            result.onSuccess { response ->
                logger.logIfTraceEnabled(id) {
                    "Successfully fetched Moments API response: $response"
                }
            }.onFailure { ex ->
                logger.logIfErrorEnabled(id) {
                    "Failed to fetch Moments API response: ${ex.message}"
                }
            }
            callback.onComplete(result)
        }.addTo(disposables)
    }
    
    override fun updateConfiguration(configuration: DataObject): Module? {
        val moduleConfiguration = MomentsApiConfiguration.fromDataObject(configuration)
        if (moduleConfiguration == null) {
            logger.logIfWarnEnabled(id) {
                "Moments API module configuration update failed: region is required but missing from configuration"
            }
            return null
        }
        service.updateConfiguration(moduleConfiguration)
        return this
    }
    
    override fun onShutdown() {
        disposables.dispose()
    }

    override val id: String = MomentsApi.ID

    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    class Factory(
        enforcedSettings: DataObject? = null
    ) : ModuleFactory {

        constructor(moduleSettings: MomentsApiSettingsBuilder) : this(moduleSettings.build())

        private val enforcedSettings = enforcedSettings?.let { listOf(it) }
            ?: emptyList()

        override val moduleType: String = MomentsApi.ID

        override fun getEnforcedSettings(): List<DataObject> = enforcedSettings

        override fun create(
            moduleId: String,
            context: TealiumContext,
            configuration: DataObject
        ): Module? {
            val moduleConfiguration = MomentsApiConfiguration.fromDataObject(configuration)
            if (moduleConfiguration == null) {
                context.logger.logIfWarnEnabled(moduleId) {
                    "Moments API module cannot be created: region is required but missing from configuration"
                }
                return null
            }
            return MomentsApiModule(context, moduleConfiguration)
        }
    }
}
