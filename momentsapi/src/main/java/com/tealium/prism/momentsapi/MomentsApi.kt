package com.tealium.prism.momentsapi

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.pubsub.Single
import com.tealium.prism.momentsapi.internal.MomentsApiModule
import com.tealium.prism.momentsapi.internal.MomentsApiWrapper

/**
 * The MomentsApi module retrieves visitor profile data from Tealium AudienceStream.
 */
interface MomentsApi {
    /**
     * Fetches visitor data from a configured MomentsApi Engine.
     *
     * @param engineId The ID of the MomentsApi engine to fetch data from
     * @return Single containing either a successful [EngineResponse] or an Exception
     */
    fun fetchEngineResponse(engineId: String): Single<TealiumResult<EngineResponse>>

    companion object {
        /**
         * The [Module.id] of the [MomentsApi] module.
         */
        const val ID = "MomentsAPI"

        /**
         * Returns a configured [ModuleFactory] for enabling the [MomentsApi] Module.
         *
         * The [enforcedSettings] will be set for the lifetime of [Tealium] instance that this [ModuleFactory]
         * is loaded in, and these settings will override any that come from other local/remote sources.
         *
         * @param enforcedSettings
         *  MomentsApi settings that should override any from any other settings source.
         *  Pass `null` to initialize this module only when some Local or Remote settings are provided.
         *  Omitting this parameter will initialize the module with its default settings.
         */
        @JvmStatic
        @JvmOverloads
        fun configure(
            enforcedSettings: ((MomentsApiSettingsBuilder) -> MomentsApiSettingsBuilder)? = { it }
        ): ModuleFactory {
            val enforcedSettingsBuilder = enforcedSettings?.invoke(MomentsApiSettingsBuilder())
            return MomentsApiModule.Factory(enforcedSettingsBuilder?.build())
        }

        /**
         * Returns the MomentsApi instance for a given Tealium instance
         */
        @JvmStatic
        fun getInstance(tealium: Tealium): MomentsApi {
            return MomentsApiWrapper(tealium)
        }
    }
}

/**
 * Returns the default [ModuleFactory] implementation that will not create any instances
 * unless there are settings provided from Local or Remote sources.
 */
@JvmField
val DEFAULT_FACTORY: ModuleFactory = MomentsApiModule.Factory(null)

/**
 * Returns a configured [ModuleFactory] for enabling the MomentsApi Module.
 *
 * The [enforcedSettings] will be set for the lifetime of [Tealium] instance that this [ModuleFactory]
 * is loaded in, and these settings will override any that come from other local/remote sources.
 *
 * @param enforcedSettings
 *  MomentsApi settings that should override any from any other settings source.
 *  Pass `null` to initialize this module only when some Local or Remote settings are provided.
 *  Omitting this parameter will initialize the module with its default settings.
 */
@JvmOverloads
fun com.tealium.prism.core.api.Modules.momentsApi(
    enforcedSettings: ((MomentsApiSettingsBuilder) -> MomentsApiSettingsBuilder)? = { it }
): ModuleFactory =
    MomentsApi.configure(enforcedSettings)

/**
 * The [Module.id] of the [MomentsApi] module.
 */
val com.tealium.prism.core.api.Modules.Types.MOMENTS_API
    get() = MomentsApi.ID

/**
 * Returns the MomentsApi instance for a given Tealium instance
 */
val Tealium.momentsApi: MomentsApi
    get() = MomentsApi.getInstance(this)

