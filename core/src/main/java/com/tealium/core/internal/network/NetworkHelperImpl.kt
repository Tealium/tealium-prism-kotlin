package com.tealium.core.internal.network

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.logger.Logs
import com.tealium.core.api.network.*
import com.tealium.core.internal.LogCategory
import org.json.JSONException
import org.json.JSONObject

private const val ETAG_KEY = "etag"

/**
 * Default implementation of [NetworkHelper] to make asynchronous basic HTTP requests.
 */
class NetworkHelperImpl(
    private val networkClient: NetworkClient,
    private val logger: Logger,
) : NetworkHelper {

    private fun send(request: HttpRequest, completion: (NetworkResult) -> Unit): Disposable {
        val loggedCompletion: (NetworkResult) -> Unit = { result ->
            val resultLogger: Logs? = when (result) {
                is Success -> logger.trace
                is Failure -> logger.error
            }
            resultLogger?.log(
                LogCategory.HTTP_CLIENT,
                "Completed request ${request.url} ${request.body} with $result"
            )
            completion(result)
        }
        return networkClient.sendRequest(request, loggedCompletion)
    }

    override fun get(url: String, etag: String?, completion: (NetworkResult) -> Unit): Disposable {
        return send(HttpRequest.get(url, etag), completion)
    }

    override fun post(
        url: String,
        payload: TealiumBundle?,
        completion: (NetworkResult) -> Unit
    ): Disposable {
        return send(HttpRequest.post(url, payload, true), completion)
    }

    override fun getJson(
        url: String,
        etag: String?,
        completion: (JSONObject?) -> Unit
    ): Disposable {
        return send(HttpRequest.get(url, etag)) { result ->
            if (result is Success && result.httpResponse.body != null) {
                try {
                    val json = JSONObject(result.httpResponse.body)
                    val etagHeader = result.httpResponse.headers[ETAG_KEY]?.firstOrNull()
                    etagHeader?.let {
                        json.put(ETAG_KEY, it)
                    }
                    completion(json)
                    return@send
                } catch (ignore: JSONException) {
                    logger.debug?.log("NetworkHelper", "Invalid")
                }
            }

            completion(null)
        }
    }

    override fun getTealiumBundle(
        url: String,
        etag: String?,
        completion: (TealiumBundle?) -> Unit
    ): Disposable {
        return send(HttpRequest.get(url, etag)) { result ->
            if (result is Success && result.httpResponse.body != null) {
                try {
                    var bundle = TealiumBundle.fromString(result.httpResponse.body)
                    val etagHeader = result.httpResponse.headers[ETAG_KEY]?.firstOrNull()
                    etagHeader?.let {
                        bundle = bundle?.copy {
                            put(ETAG_KEY, it)
                        }
                    }

                    completion(bundle)
                    return@send
                } catch (ignore: JSONException) {
                    logger.debug?.log("NetworkHelper", "Invalid")
                    // log
                }
            }

            completion(null)
        }
    }
}