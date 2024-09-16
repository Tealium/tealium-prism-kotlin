package com.tealium.core.api.network

import java.net.URL

/**
 * Represents the response data for an HTTP response
 *
 * @param url The original URL of the request that returned this response
 * @param statusCode The HTTP status code of the response.
 * @param message The response message from [java.net.HttpURLConnection] if available.
 * @param headers The HTTP response headers for this request
 * @param body Optional body returned by the response
 */
data class HttpResponse(
    val url: URL,
    val statusCode: Int,
    val message: String,
    val headers: Map<String, List<String>>,
    val body: String? = null,
) {
    /**
     * Utility method to retrieve the `etag` from the [headers] field.
     */
    val etag: String?
        get() = headers[HttpRequest.Headers.ETAG]?.firstOrNull()
}