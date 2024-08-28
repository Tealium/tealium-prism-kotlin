package com.tealium.core.internal.network

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.logger.Logs
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.NetworkClient
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.network.NetworkError.UnexpectedError
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.NetworkResult.Failure
import com.tealium.core.api.network.NetworkResult.Success
import com.tealium.core.internal.logger.LogCategory
import com.tealium.core.internal.pubsub.CompletedDisposable
import org.json.JSONException
import org.json.JSONObject
import java.net.MalformedURLException
import java.net.URL

private const val ETAG_KEY = "etag"

/**
 * Default implementation of [NetworkHelper] to make asynchronous basic HTTP requests.
 */
class NetworkHelperImpl(
    private val networkClient: NetworkClient,
    private val logger: Logger,
) : NetworkHelper {

    private fun loggedCompletion(completion: (NetworkResult) -> Unit): (NetworkResult) -> Unit =
        { result ->
            when (result) {
                is Success -> logger.trace
                is Failure -> logger.error
            }?.log(
                LogCategory.NETWORK_HELPER,
                "Completed request with $result"
            )
            completion(result)
        }

    private fun send(
        builder: HttpRequest.Builder,
        completion: (NetworkResult) -> Unit
    ): Disposable {
        val loggedCompletion = loggedCompletion(completion)

        return try {
            val httpRequest = builder.build()
            logger.trace?.log(LogCategory.NETWORK_HELPER, "Built request $httpRequest")

            networkClient.sendRequest(httpRequest, loggedCompletion)
        } catch (e: MalformedURLException) {
            logger.error?.log(LogCategory.NETWORK_HELPER, "Failed to build request")

            loggedCompletion(Failure(UnexpectedError(e)))
            CompletedDisposable
        }
    }

    override fun get(url: String, etag: String?, completion: (NetworkResult) -> Unit): Disposable {
        return send(HttpRequest.get(url, etag), completion)
    }

    override fun get(url: URL, etag: String?, completion: (NetworkResult) -> Unit): Disposable {
        return send(HttpRequest.get(url, etag), completion)
    }

    override fun post(
        url: String,
        payload: TealiumBundle?,
        completion: (NetworkResult) -> Unit
    ): Disposable {
        return send(HttpRequest.post(url, payload.toString()).gzip(true), completion)
    }

    override fun post(
        url: URL,
        payload: TealiumBundle?,
        completion: (NetworkResult) -> Unit
    ): Disposable {
        return send(HttpRequest.post(url, payload.toString()).gzip(true), completion)
    }

    override fun getJson(
        url: String,
        etag: String?,
        completion: (JSONObject?) -> Unit
    ): Disposable {
        return send(HttpRequest.get(url, etag), handleJsonResult(completion))
    }

    override fun getJson(url: URL, etag: String?, completion: (JSONObject?) -> Unit): Disposable {
        return send(HttpRequest.get(url, etag), handleJsonResult(completion))
    }

    private fun handleJsonResult(completion: (JSONObject?) -> Unit): (NetworkResult) -> Unit =
        { result ->
            var jsonResult: JSONObject? = null
            if (result is Success && result.httpResponse.body != null) {
                try {
                    val json = JSONObject(result.httpResponse.body)
                    val etagHeader = result.httpResponse.headers[ETAG_KEY]?.firstOrNull()
                    etagHeader?.let {
                        json.put(ETAG_KEY, it)
                    }
                    jsonResult = json
                } catch (ignore: JSONException) {
                    logger.debug?.log("NetworkHelper", "Invalid")
                }
            }
            completion(jsonResult)
        }


    override fun getTealiumBundle(
        url: String,
        etag: String?,
        completion: (TealiumBundle?) -> Unit
    ): Disposable {
        return send(HttpRequest.get(url, etag), handleBundleResult(completion))
    }

    override fun getTealiumBundle(
        url: URL,
        etag: String?,
        completion: (TealiumBundle?) -> Unit
    ): Disposable {
        return send(HttpRequest.get(url, etag), handleBundleResult(completion))
    }

    private fun handleBundleResult(completion: (TealiumBundle?) -> Unit): (NetworkResult) -> Unit =
        { result ->
            var bundleResult: TealiumBundle? = null
            if (result is Success && result.httpResponse.body != null) {
                try {
                    var bundle = TealiumBundle.fromString(result.httpResponse.body)
                    val etagHeader = result.httpResponse.headers[ETAG_KEY]?.firstOrNull()
                    etagHeader?.let {
                        bundle = bundle?.copy {
                            put(ETAG_KEY, it)
                        }
                    }

                    bundleResult = bundle
                } catch (ignore: JSONException) {
                    logger.debug?.log("NetworkHelper", "Invalid")
                }
            }

            completion(bundleResult)
        }
}