package com.tealium.core.api.network

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.pubsub.Disposable
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
    fun get(url: String, etag: String?, completion: (NetworkResult) -> Unit) : Disposable

    /**
     * Asynchronously POSTs the [payload] to the given [url].
     *
     * @param url The Url to GET
     * @param payload The body to be POSTed
     */
    fun post(url: String, payload: TealiumBundle?, completion: (NetworkResult) -> Unit): Disposable

    /**
     * Asynchronously fetches the given [url] and returns the payload parsed as a [JSONObject]
     * An optional [etag] can be supplied if the resource being requested is already available on
     * the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     */
    fun getJson(url: String, etag: String?, completion: (JSONObject?) -> Unit): Disposable

    /**
     * Asynchronously fetches the given [url] and returns the payload parsed as a [TealiumBundle]
     * An optional [etag] can be supplied if the resource being requested is already available on
     * the device and may be able to be re-used.
     *
     * @param url The Url to GET
     * @param etag Optional etag of the currently known resource
     */
    fun getTealiumBundle(url: String, etag: String?, completion: (TealiumBundle?) -> Unit): Disposable
}