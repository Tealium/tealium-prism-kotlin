package com.tealium.core.internal.network

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.NetworkClient
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.Success
import org.json.JSONException
import org.json.JSONObject

/**
 * Default implementation of [NetworkHelper] to make asynchronous basic HTTP requests.
 */
class NetworkHelperImpl(
    private val networkClient: NetworkClient,
) : NetworkHelper {

    override fun get(url: String, etag: String?, completion: (NetworkResult) -> Unit): Disposable {
        return networkClient.sendRequest(HttpRequest.get(url, etag), completion)
    }

    override fun post(
        url: String,
        payload: TealiumBundle?,
        completion: (NetworkResult) -> Unit
    ): Disposable {
        return networkClient.sendRequest(HttpRequest.post(url, payload, true), completion)
    }

    override fun getJson(
        url: String,
        etag: String?,
        completion: (JSONObject?) -> Unit
    ): Disposable {
        return networkClient.sendRequest(HttpRequest.get(url, etag)) { result ->
            if (result is Success && result.httpResponse.body != null) {
                try {
                    val json = JSONObject(result.httpResponse.body)
                    completion(json)
                    return@sendRequest
                } catch (ignore: JSONException) {
                    // log
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
        return networkClient.sendRequest(HttpRequest.get(url, etag)) { result ->
            if (result is Success && result.httpResponse.body != null) {
                try {
                    val bundle = TealiumBundle.fromString(result.httpResponse.body)
                    completion(bundle)
                    return@sendRequest
                } catch (ignore: JSONException) {
                    // log
                }
            }

            completion(null)
        }
    }
}