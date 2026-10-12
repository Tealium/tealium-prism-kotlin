package com.tealium.prism.contextapi.internal

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfErrorEnabled
import com.tealium.prism.core.api.logger.logIfTraceEnabled
import com.tealium.prism.core.api.logger.logIfWarnEnabled
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.misc.failure
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.modules.ModuleShutdownException
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.modules.VisitorIdProvider
import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposables
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
    private val moduleManager: ModuleManager,
    private val logger: Logger,
    private var service: ContextApiService
) : Module {

    constructor(
        context: TealiumContext,
        configuration: ContextApiConfiguration,
    ) : this(
        context.moduleManager,
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
        // fails the request once if this module shuts down before it completes
        val pending = Disposables.composite()
        Disposables.subscription { callback.failure(ModuleShutdownException(id)) }.addTo(pending)
        disposables.add(pending)
        if (pending.isDisposed) return

        moduleManager.getModuleOfType(VisitorIdProvider::class.java) { provider ->
            if (provider == null) {
                disposables.remove(pending)
                logger.logIfErrorEnabled(id) {
                    "Failed to fetch Context API response: visitor ID handling is disabled"
                }
                callback.failure(
                    ModuleNotEnabledException("Module (${Modules.Types.VISITOR_ID}) is not enabled.")
                )
            } else {
                fetchEngineResponse(engineId, provider.visitorId.value, pending, callback)
            }
        }.addTo(pending)
    }

    private fun fetchEngineResponse(
        engineId: String,
        visitorId: String,
        pending: CompositeDisposable,
        callback: Callback<TealiumResult<EngineResponse>>
    ) {
        logger.logIfTraceEnabled(id) {
            "Fetching ContextApi response for engine: $engineId, visitor: $visitorId"
        }
        
        service.fetchEngineResponse(engineId, visitorId) { result ->
            // shut down while in flight: the request already failed with ModuleShutdownException
            if (pending.isDisposed) return@fetchEngineResponse
            disposables.remove(pending)
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
        }.addTo(pending)
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
