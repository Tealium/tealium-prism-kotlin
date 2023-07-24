package com.tealium.core.internal.network

import java.net.URL

/**
 * Represents the response data for an HTTP response
 */
data class HttpResponseData(
    val url: URL,
    val statusCode: Int,
    val message: String,
    val headers: Map<String, List<String>>,
    val body: String?,
)