package com.tealium.prism.contextapi

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleShutdownException
import com.tealium.prism.core.api.network.NetworkException
import com.tealium.prism.core.api.pubsub.Single
import com.tealium.prism.contextapi.internal.ContextApiModule
import com.tealium.prism.contextapi.internal.ContextApiWrapper

/**
 * The ContextApi module retrieves visitor profile data from Tealium AudienceStream.
 */
interface ContextApi {
    /**
     * Fetches visitor data from a configured Context API engine, for the current visitor id.
     *
     * The result is a failure carrying:
     *  - [ModuleNotEnabledException] when the Context API module is not enabled, or when the
     *    VisitorId module is disabled, as fetching requires the visitor id
     *  - [ModuleShutdownException] when the Context API module is shut down before the request
     *    completes
     *  - [Tealium.TealiumShutdownException], or the initialization failure, when the [Tealium]
     *    instance was shut down or failed to initialize
     *  - otherwise, the exception that stopped the request, such as a [NetworkException] or a
     *    [ContextApiConfigurationException]
     *
     *  @param engineId The ID of the Context API engine to fetch data from; must not be empty, or
     *   the result is a failure carrying an [IllegalArgumentException]
     *  @return A [Single] that completes with the [EngineResponse], or with one of the failures above
     */
    fun fetchEngineResponse(engineId: String): Single<TealiumResult<EngineResponse>>

    companion object {
        /**
         * The [Module.id] of the [ContextApi] module.
         */
        const val ID = "ContextAPI"

        /**
         * Returns a configured [ModuleFactory] for enabling the [ContextApi] Module.
         *
         * The [enforcedSettings] will be set for the lifetime of [Tealium] instance that this [ModuleFactory]
         * is loaded in, and these settings will override any that come from other local/remote sources.
         *
         * @param enforcedSettings
         *  ContextApi settings that should override any from any other settings source.
         *  Pass `null` to initialize this module only when some Local or Remote settings are provided.
         *  Omitting this parameter will initialize the module with its default settings.
         */
        @JvmStatic
        @JvmOverloads
        fun configure(
            enforcedSettings: ((ContextApiSettingsBuilder) -> ContextApiSettingsBuilder)? = { it }
        ): ModuleFactory {
            val enforcedSettingsBuilder = enforcedSettings?.invoke(ContextApiSettingsBuilder())
            return ContextApiModule.Factory(enforcedSettingsBuilder?.build())
        }

        /**
         * Returns the ContextApi instance for a given Tealium instance
         */
        @JvmStatic
        fun getInstance(tealium: Tealium): ContextApi {
            return ContextApiWrapper(tealium)
        }
    }
}

/**
 * Returns the default [ModuleFactory] implementation that will not create any instances
 * unless there are settings provided from Local or Remote sources.
 */
@JvmField
val DEFAULT_FACTORY: ModuleFactory = ContextApiModule.Factory(null)

/**
 * Returns a configured [ModuleFactory] for enabling the ContextApi Module.
 *
 * The [enforcedSettings] will be set for the lifetime of [Tealium] instance that this [ModuleFactory]
 * is loaded in, and these settings will override any that come from other local/remote sources.
 *
 * @param enforcedSettings
 *  ContextApi settings that should override any from any other settings source.
 *  Pass `null` to initialize this module only when some Local or Remote settings are provided.
 *  Omitting this parameter will initialize the module with its default settings.
 */
@JvmOverloads
fun com.tealium.prism.core.api.Modules.contextApi(
    enforcedSettings: ((ContextApiSettingsBuilder) -> ContextApiSettingsBuilder)? = { it }
): ModuleFactory =
    ContextApi.configure(enforcedSettings)

/**
 * The [Module.id] of the [ContextApi] module.
 */
val com.tealium.prism.core.api.Modules.Types.CONTEXT_API
    get() = ContextApi.ID

/**
 * Returns the ContextApi instance for a given Tealium instance
 */
val Tealium.contextApi: ContextApi
    get() = ContextApi.getInstance(this)

