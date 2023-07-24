package com.tealium.core.internal.network

import java.net.URL

/**
 * Represents the data for an HTTP request
 *
 * @property url The URL to which the request will be sent.
 * @property method The HTTP method of the request.
 * @property body The optional body of the request as a string.
 * @property headers The optional headers to be included in the request as a map of key-value pairs.
 * @property isGzip Indicates whether the request body should be compressed using GZIP encoding. Default is false.
 */
data class HttpRequestData(
    val url: URL,
    val method: HttpMethod,
    val body: String? = null,
    val headers: Map<String, String>? = null,
    val isGzip: Boolean = false,
)
