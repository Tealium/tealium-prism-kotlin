package com.tealium.core.internal.network

import com.tealium.core.api.data.Deserializer
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.network.DeserializedNetworkCallback
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.NetworkCallback
import com.tealium.core.api.network.NetworkClient
import com.tealium.core.api.network.NetworkException
import com.tealium.core.api.network.NetworkException.UnexpectedException
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.NetworkResult.Failure
import com.tealium.core.api.network.NetworkResult.Success
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.internal.logger.LogCategory
import com.tealium.core.internal.pubsub.CompletedDisposable
import org.json.JSONObject
import java.net.MalformedURLException
import java.net.URL

/**
 * Default implementation of [NetworkHelper] to make asynchronous basic HTTP requests.
 */
class NetworkHelperImpl(
    private val networkClient: NetworkClient,
    private val logger: Logger,
) : NetworkHelper {

    private fun loggedCompletion(completion: NetworkCallback<NetworkResult>): NetworkCallback<NetworkResult> =
        NetworkCallback { result ->
            when (result) {
                is Success -> logger.trace
                is Failure -> logger.error
            }?.log(
                LogCategory.NETWORK_HELPER,
                "Completed request with $result"
            )
            completion.onComplete(result)
        }

    private fun send(
        builder: HttpRequest.Builder,
        completion: NetworkCallback<NetworkResult>
    ): Disposable {
        val loggedCompletion = loggedCompletion(completion)

        return try {
            val httpRequest = builder.build()
            logger.trace?.log(LogCategory.NETWORK_HELPER, "Built request $httpRequest")

            networkClient.sendRequest(httpRequest, loggedCompletion)
        } catch (e: MalformedURLException) {
            logger.error?.log(LogCategory.NETWORK_HELPER, "Failed to build request")

            loggedCompletion.onComplete(Failure(UnexpectedException(e)))
            CompletedDisposable
        }
    }

    override fun get(url: String, etag: String?, completion: NetworkCallback<NetworkResult>): Disposable =
        send(HttpRequest.get(url, etag), completion)

    override fun get(url: URL, etag: String?, completion: NetworkCallback<NetworkResult>): Disposable =
        send(HttpRequest.get(url, etag), completion)

    override fun post(
        url: String,
        payload: DataObject?,
        completion: NetworkCallback<NetworkResult>
    ): Disposable = send(HttpRequest.post(url, payload.toString()).gzip(true), completion)

    override fun post(
        url: URL,
        payload: DataObject?,
        completion: NetworkCallback<NetworkResult>
    ): Disposable = send(HttpRequest.post(url, payload.toString()).gzip(true), completion)

    override fun getJson(
        url: String,
        etag: String?,
        completion: DeserializedNetworkCallback<JSONObject>
    ): Disposable = getDeserializable(url, etag, ::JSONObject, completion)

    override fun getJson(
        url: URL,
        etag: String?,
        completion: DeserializedNetworkCallback<JSONObject>
    ): Disposable = getDeserializable(url, etag, ::JSONObject, completion)

    override fun getDataObject(
        url: String,
        etag: String?,
        completion: DeserializedNetworkCallback<DataObject>
    ): Disposable =
        getDataItemConvertible(url, etag, DataObject.Converter, completion)

    override fun getDataObject(
        url: URL,
        etag: String?,
        completion: DeserializedNetworkCallback<DataObject>
    ): Disposable =
        getDataItemConvertible(url, etag, DataObject.Converter, completion)

    override fun <T> getDataItemConvertible(
        url: String,
        etag: String?,
        converter: DataItemConverter<T>,
        completion: DeserializedNetworkCallback<T>
    ): Disposable = getDeserializable(url, etag, dataItemDeserializer(converter), completion)

    override fun <T> getDataItemConvertible(
        url: URL,
        etag: String?,
        converter: DataItemConverter<T>,
        completion: DeserializedNetworkCallback<T>
    ): Disposable = getDeserializable(url, etag, dataItemDeserializer(converter), completion)

    private fun <T> dataItemDeserializer(converter: DataItemConverter<T>) =
        { str: String ->
            val value = DataItem.parse(str)
            converter.convert(value)
        }

    override fun <T> getDeserializable(
        url: String,
        etag: String?,
        deserializer: Deserializer<String, T?>,
        completion: DeserializedNetworkCallback<T>
    ): Disposable = send(
        HttpRequest.get(url, etag)
    ) { networkResult ->
        val result = deserializeResult(networkResult, deserializer)

        completion.onComplete(result)
    }

    override fun <T> getDeserializable(
        url: URL,
        etag: String?,
        deserializer: Deserializer<String, T?>,
        completion: DeserializedNetworkCallback<T>
    ): Disposable = send(
        HttpRequest.get(url, etag),
    ) { networkResult ->
        val result = deserializeResult(networkResult, deserializer)

        completion.onComplete(result)
    }

    private fun <T> deserializeResult(
        result: NetworkResult,
        deserializer: Deserializer<String, T?>
    ): TealiumResult<NetworkHelper.HttpValue<T>> {
        if (result is Failure) {
            return TealiumResult.failure(result.networkException)
        }

        var error: Throwable? = null
        if (result is Success && result.httpResponse.body != null) {
            try {
                val deserialized = deserializer.deserialize(result.httpResponse.body)
                if (deserialized != null) {
                    return TealiumResult.success(
                        NetworkHelper.HttpValue(
                            deserialized,
                            result.httpResponse
                        )
                    )
                }
            } catch (e: Exception) {
                logger.debug?.log("NetworkHelper", "Invalid")
                error = e
            }
        }

        return TealiumResult.failure(NetworkException.UnexpectedException(error))
    }
}