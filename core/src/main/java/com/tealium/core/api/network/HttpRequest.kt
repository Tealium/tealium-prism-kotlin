package com.tealium.core.api.network

import com.tealium.core.api.data.TealiumBundle

/**
 * Represents the data for an HTTP request
 *
 * @property url The URL to which the request will be sent.
 * @property method The HTTP method of the request.
 * @property body The optional body of the request as a string.
 * @property headers The optional headers to be included in the request as a map of key-value pairs.
 * @property isGzip Indicates whether the request body should be compressed using GZIP encoding. Default is false.
 */
data class HttpRequest(
    val url: String,
    val method: HttpMethod,
    val body: String? = null,
    val headers: Map<String, String>? = null,
    val isGzip: Boolean = false,
) {
    companion object {

        /**
         * Utility method for building a simple POST request.
         *
         * @param destination The url to be POSTed to
         * @param payload The body of the POST request
         * @param gzip true to gzip compress the [payload]; otherwise false
         */
        @JvmStatic
        fun post(destination: String, payload: TealiumBundle?, gzip: Boolean): HttpRequest {
            return HttpRequest(destination, HttpMethod.Post, body = payload.toString(), isGzip = gzip)
        }

        /**
         * Utility method for building a simple GET request.
         *
         * @param destination the url to GET
         * @param etag optional etag allowing the server to efficiently return the appropriate
         * response if the data requested hasn't changed.
         */
        @JvmStatic
        fun get(destination: String, etag: String? = null): HttpRequest {
            val etagHeader = when {
                etag.isNullOrEmpty() -> null
                else -> mapOf("etag" to etag)
            }

            return HttpRequest(destination, HttpMethod.Get, headers = etagHeader)
        }
    }
}
