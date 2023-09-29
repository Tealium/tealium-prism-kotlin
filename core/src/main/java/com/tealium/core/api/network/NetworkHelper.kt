package com.tealium.core.api.network

import com.tealium.core.api.data.TealiumBundle
import org.json.JSONObject

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
    suspend fun get(url: String, etag: String?): NetworkResult

    /**
     * Asynchronously POSTs the [payload] to the given [url].
     *
     * @param url The Url to GET
     * @param payload The body to be POSTed
     */
    suspend fun post(url: String, payload: TealiumBundle?): NetworkResult

    /**
     * Asynchronously fetches the given [url] and returns the payload parsed as a [JSONObject]
     * An optional [etag] can be supplied if the resource being requested is already available on
     * the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     */
    suspend fun getJson(url: String, etag: String?): JSONObject?

    /**
     * Asynchronously fetches the given [url] and returns the payload parsed as a [TealiumBundle]
     * An optional [etag] can be supplied if the resource being requested is already available on
     * the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     */
    suspend fun getTealiumBundle(url: String, etag: String?): TealiumBundle?
}