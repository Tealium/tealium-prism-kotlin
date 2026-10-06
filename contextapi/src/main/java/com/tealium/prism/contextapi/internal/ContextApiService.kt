package com.tealium.prism.contextapi.internal

import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.misc.failure
import com.tealium.prism.core.api.misc.success
import com.tealium.prism.core.api.network.NetworkHelper
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.contextapi.EngineResponse
import com.tealium.prism.contextapi.ContextApiConfigurationException
import java.net.MalformedURLException
import java.net.URL

/**
 * The [ContextApiService] manages HTTP requests to the ContextApi and provides methods for fetching
 * visitor profile data from configured ContextApi engines.
 */
interface ContextApiService {
    /**
     * Fetches visitor data from the ContextApi engine.
     *
     * @param engineId The ID of the ContextApi engine
     * @param visitorId The visitor ID to fetch data for
     * @param completion Callback to receive the result
     * @return Disposable to cancel the request
     */
    fun fetchEngineResponse(
        engineId: String,
        visitorId: String,
        completion: Callback<TealiumResult<EngineResponse>>
    ): Disposable

    /**
     * Updates the configuration of the service.
     *
     * @param configuration The new configuration to apply
     */
    fun updateConfiguration(configuration: ContextApiConfiguration)
}

/**
 * Implementation of the ContextApi Service. This class provides the logic for making HTTP requests
 * to the ContextApi and handling responses.
 */
class ContextApiServiceImpl(
    private val networkHelper: NetworkHelper,
    private val account: String,
    private val profile: String,
    private val environment: String,
    private var configuration: ContextApiConfiguration
) : ContextApiService {

    private val defaultReferrer: String =
        "https://tags.tiqcdn.com/utag/$account/$profile/$environment/mobile.html"

    /**
     * Fetches visitor data from the ContextApi engine.
     *
     * @param engineId The ID of the ContextApi engine
     * @param visitorId The visitor ID to fetch data for
     * @param completion Callback to receive the result
     * @return Disposable to cancel the request
     */
    override fun fetchEngineResponse(
        engineId: String,
        visitorId: String,
        completion: Callback<TealiumResult<EngineResponse>>
    ): Disposable {
        if (engineId.isEmpty()) {
            completion.failure(IllegalArgumentException("Invalid engine ID provided"))
            return Disposables.disposed()
        }

        val url = try {
            buildURL(engineId, visitorId)
        } catch (e: Exception) {
            completion.failure(ContextApiConfigurationException("Configuration error: Failed to build Context API URL", e))
            return Disposables.disposed()
        }

        val referrerValue = configuration.referrer ?: defaultReferrer
        val headers = mapOf(
            "Accept" to "application/json",
            "Referer" to referrerValue
        )

        return networkHelper.getDataItemConvertible(
            url = url,
            etag = null,
            additionalHeaders = headers,
            converter = Converters.EngineResponseConverter
        ) { result ->
            result.onSuccess { engineResponse ->
                completion.success(engineResponse.value)
            }.onFailure { e ->
                completion.failure(e)
            }
        }
    }

    @Throws(MalformedURLException::class)
    private fun buildURL(engineId: String, visitorId: String): URL {
        val urlString =
            "https://personalization-api.${configuration.region.value}.prod.tealiumapis.com/personalization/accounts/$account/profiles/$profile/engines/$engineId/visitors/$visitorId?ignoreTapid=true"
        return URL(urlString)
    }

    override fun updateConfiguration(configuration: ContextApiConfiguration) {
        this.configuration = configuration
    }
}
