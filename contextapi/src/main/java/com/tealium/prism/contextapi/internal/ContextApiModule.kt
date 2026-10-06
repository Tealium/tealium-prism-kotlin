package com.tealium.prism.contextapi.internal

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
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Single
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.contextapi.BuildConfig
import com.tealium.prism.contextapi.EngineResponse
import com.tealium.prism.contextapi.ContextApi
import com.tealium.prism.contextapi.ContextApiSettingsBuilder

class ContextApiWrapper(
    private val moduleProxy: ModuleProxy<ContextApiModule>
) : ContextApi {
    constructor(tealium: Tealium) : this(
        tealium.createModuleProxy(ContextApiModule::class.java)
    )

    override fun fetchEngineResponse(engineId: String): Single<TealiumResult<EngineResponse>> {
        return moduleProxy.executeAsyncModuleTask { module, callback ->
            module.fetchEngineResponse(engineId, callback)
        }
    }
}

/**
 * Internal ContextApi module implementation.
 */
class ContextApiModule(
    private val visitorId: ObservableState<String>,
    private val logger: Logger,
    private var service: ContextApiService
) : Module {

    constructor(
        context: TealiumContext,
        configuration: ContextApiConfiguration,
    ) : this(
        context.visitorId,
        context.logger,
        ContextApiServiceImpl(
            networkHelper = context.network.networkHelper,
            account = context.config.accountName,
            profile = context.config.profileName,
            environment = context.config.environment,
            configuration = configuration
        )
    )

    private val disposables: CompositeDisposable = Disposables.composite()
    
    fun fetchEngineResponse(engineId: String, callback: Callback<TealiumResult<EngineResponse>>) {
        val visitorId = visitorId.value

        logger.logIfTraceEnabled(id) {
            "Fetching ContextApi response for engine: $engineId, visitor: $visitorId"
        }
        
        service.fetchEngineResponse(engineId, visitorId) { result ->
            result.onSuccess { response ->
                logger.logIfTraceEnabled(id) {
                    "Successfully fetched Context API response: $response"
                }
            }.onFailure { ex ->
                logger.logIfErrorEnabled(id) {
                    "Failed to fetch Context API response: ${ex.message}"
                }
            }
            callback.onComplete(result)
        }.addTo(disposables)
    }
    
    override fun updateConfiguration(configuration: DataObject): Module? {
        val moduleConfiguration = ContextApiConfiguration.fromDataObject(configuration)
        if (moduleConfiguration == null) {
            logger.logIfWarnEnabled(id) {
                "Context API module configuration update failed: region is required but missing from configuration"
            }
            return null
        }
        service.updateConfiguration(moduleConfiguration)
        return this
    }
    
    override fun onShutdown() {
        disposables.dispose()
    }

    override val id: String = ContextApi.ID

    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    class Factory(
        enforcedSettings: DataObject? = null
    ) : ModuleFactory {

        constructor(moduleSettings: ContextApiSettingsBuilder) : this(moduleSettings.build())

        private val enforcedSettings = enforcedSettings?.let { listOf(it) }
            ?: emptyList()

        override val moduleType: String = ContextApi.ID

        override fun getEnforcedSettings(): List<DataObject> = enforcedSettings

        override fun create(
            moduleId: String,
            context: TealiumContext,
            configuration: DataObject
        ): Module? {
            val moduleConfiguration = ContextApiConfiguration.fromDataObject(configuration)
            if (moduleConfiguration == null) {
                context.logger.logIfWarnEnabled(moduleId) {
                    "Context API module cannot be created: region is required but missing from configuration"
                }
                return null
            }
            return ContextApiModule(context, moduleConfiguration)
        }
    }
}
