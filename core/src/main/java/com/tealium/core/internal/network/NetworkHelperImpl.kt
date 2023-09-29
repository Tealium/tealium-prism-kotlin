package com.tealium.core.internal.network

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.network.Failure
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.NetworkClient
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.Success
import com.tealium.core.api.network.UnexpectedError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Default implementation of [NetworkHelper] to make asynchronous basic HTTP requests.
 */
class NetworkHelperImpl(
    private val networkClient: NetworkClient,
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : NetworkHelper {

    override suspend fun get(url: String, etag: String?): NetworkResult {
        return send(HttpRequest.get(url, etag))
    }

    override suspend fun post(url: String, payload: TealiumBundle?): NetworkResult {
        return send(HttpRequest.post(url, payload, true))
    }

    override suspend fun getJson(url: String, etag: String?): JSONObject? {
        return when (val result = send(HttpRequest.get(url, etag))) {
            is Success -> {
                if (result.httpResponse.body != null) {
                    if (isValidJson(result.httpResponse.body)) {
                        JSONObject(result.httpResponse.body)
                    } else {
                        // invalid JSON string
                        null
                    }
                } else {
                    null
                }
            }

            else -> null
        }
    }

    override suspend fun getTealiumBundle(url: String, etag: String?): TealiumBundle? {
        return when (val result = send(HttpRequest.get(url, etag))) {
            is Success -> {
                if (result.httpResponse.body != null) {
                    TealiumBundle.fromString(result.httpResponse.body)
                } else {
                    null
                }
            }

            else -> null
        }
    }

    private suspend fun send(request: HttpRequest): NetworkResult =
        withContext(Dispatchers.IO) {
            try {
                networkClient.sendRequestAsync(request).await()
            } catch (ex: Exception) {
                Failure(UnexpectedError(ex))
            }
        }

    // TODO - make public and move to a Utils class
    private fun isValidJson(input: String): Boolean {
        try {
            JSONTokener(input).nextValue()
        } catch (ex: JSONException) {
            return false
        }
        return true
    }
}