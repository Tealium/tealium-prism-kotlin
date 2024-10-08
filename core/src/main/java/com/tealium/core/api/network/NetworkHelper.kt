package com.tealium.core.api.network

import com.tealium.core.api.data.Deserializer
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.pubsub.Disposable
import org.json.JSONObject
import java.net.URL

typealias NetworkCallback<T> = TealiumCallback<T>
typealias DeserializedNetworkCallback<T> = NetworkCallback<TealiumResult<NetworkHelper.HttpValue<T>>>

/**
 * Utility interface for making basic async network requests. For more complex requirements, use the
 * [NetworkClient] instead.
 */
interface NetworkHelper {

    /**
     * Asynchronously fetches the given [url]. An optional [etag] can be supplied if the resource
     * being requested is already available on the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     */
    fun get(url: String, etag: String?, completion: NetworkCallback<NetworkResult>): Disposable

    /**
     * Asynchronously fetches the given [url]. An optional [etag] can be supplied if the resource
     * being requested is already available on the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     */
    fun get(url: URL, etag: String?, completion: NetworkCallback<NetworkResult>): Disposable

    /**
     * Asynchronously POSTs the [payload] to the given [url].
     *
     * @param url The Url to GET
     * @param payload The body to be POSTed
     */
    fun post(url: String, payload: DataObject?, completion: NetworkCallback<NetworkResult>): Disposable

    /**
     * Asynchronously POSTs the [payload] to the given [url].
     *
     * @param url The Url to GET
     * @param payload The body to be POSTed
     */
    fun post(url: URL, payload: DataObject?, completion: NetworkCallback<NetworkResult>): Disposable

    /**
     * Asynchronously fetches the given [url] and returns the payload parsed as a [JSONObject]
     * An optional [etag] can be supplied if the resource being requested is already available on
     * the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     */
    fun getJson(url: String, etag: String?, completion: DeserializedNetworkCallback<JSONObject>): Disposable

    /**
     * Asynchronously fetches the given [url] and returns the payload parsed as a [JSONObject]
     * An optional [etag] can be supplied if the resource being requested is already available on
     * the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     */
    fun getJson(url: URL, etag: String?, completion: DeserializedNetworkCallback<JSONObject>): Disposable

    /**
     * Asynchronously fetches the given [url] and returns the payload parsed as a [DataObject]
     * An optional [etag] can be supplied if the resource being requested is already available on
     * the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     */
    fun getDataObject(
        url: String,
        etag: String?,
        completion: DeserializedNetworkCallback<DataObject>
    ): Disposable

    /**
     * Asynchronously fetches the given [url] and returns the payload parsed as a [DataObject]
     * An optional [etag] can be supplied if the resource being requested is already available on
     * the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     */
    fun getDataObject(url: URL, etag: String?, completion: DeserializedNetworkCallback<DataObject>): Disposable

    /**
     * Asynchronously fetches the given [url] and returns the payload parsed as a [DataItem] and
     * then converted to an instance of [T] using by the given [converter].
     *
     * An optional [etag] can be supplied if the resource being requested is already available on
     * the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     * @param converter [DataItemConverter] to convert a [DataItem] to an instance of [T]
     * @param completion completion to be notified with the result
     */
    fun <T> getDataItemConvertible(
        url: URL,
        etag: String?,
        converter: DataItemConverter<T>,
        completion: DeserializedNetworkCallback<T>
    ): Disposable

    /**
     * Asynchronously fetches the given [url] and returns the payload parsed as a [DataItem] and
     * then converted to an instance of [T] using by the given [converter].
     *
     * An optional [etag] can be supplied if the resource being requested is already available on
     * the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     * @param converter [DataItemConverter] to convert a [DataItem] to an instance of [T]
     * @param completion completion to be notified with the result
     */
    fun <T> getDataItemConvertible(
        url: String,
        etag: String?,
        converter: DataItemConverter<T>,
        completion: DeserializedNetworkCallback<T>
    ): Disposable

    /**
     * Asynchronously fetches the given [url] and returns the payload parsed as a [String] and
     * then converted to an instance of [T] using by the given [deserializer].
     *
     * An optional [etag] can be supplied if the resource being requested is already available on
     * the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     * @param deserializer Deserializer to convert a [String] to an instance of [T]
     * @param completion completion to be notified with the result
     */
    fun <T> getDeserializable(
        url: String,
        etag: String?,
        deserializer: Deserializer<String, T?>,
        completion: DeserializedNetworkCallback<T>
    ): Disposable

    /**
     * Asynchronously fetches the given [url] and returns the payload parsed as a [String] and
     * then converted to an instance of [T] using by the given [deserializer].
     *
     * An optional [etag] can be supplied if the resource being requested is already available on
     * the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     * @param deserializer Deserializer to convert a [String] to an instance of [T]
     * @param completion completion to be notified with the result
     */
    fun <T> getDeserializable(
        url: URL,
        etag: String?,
        deserializer: Deserializer<String, T?>,
        completion: DeserializedNetworkCallback<T>
    ): Disposable

    /**
     * Data class for holding a generic [value] along with the [HttpResponse]
     *
     * @param value the value generated from the [httpResponse]
     * @param httpResponse The full http response
     */
    data class HttpValue<T>(
        val value: T,
        val httpResponse: HttpResponse
    )
}