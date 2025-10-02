package com.tealium.prism.core.api.network

import com.tealium.prism.core.internal.network.HttpClient
import java.io.ByteArrayInputStream
import java.net.URL
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

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
    val body: ByteArray? = null,
) {
    /**
     * Utility method to retrieve the `etag` from the [headers] field.
     */
    val etag: String?
        get() = headers[HttpRequest.Headers.ETAG]?.firstOrNull()

    /**
     * Utility method to read the [body] as a [String].
     *
     * If the `content-encoding` header is `gzip`, then the [body] will be unzipped.
     *
     * If the `content-type` header specifies a `charset`, then it will be used to convert the [body]
     * to a [String]. If no charset is present in the header, then a [fallbackCharset] can be specified
     * to read the [body], otherwise it will default to UTF-8
     *
     * @param fallbackCharset optional charset to read the bytes; default is [Charsets.UTF_8]
     */
    @JvmOverloads
    fun bodyText(fallbackCharset: Charset = Charsets.UTF_8): String? {
        if (body == null) return null

        val contentEncoding = headers[HttpRequest.Headers.CONTENT_ENCODING]
            ?.firstOrNull()
        val bytes = if (contentEncoding != null && contentEncoding.equals("gzip", ignoreCase = true)) {
            unzip(body)
        } else {
            body
        }

        val contentType = headers[HttpRequest.Headers.CONTENT_TYPE]
            ?.firstOrNull()
        val charset = parseCharset(contentType, fallbackCharset)

        return bytes.toString(charset)
    }

    private fun unzip(byteArray: ByteArray): ByteArray {
        val byteStream = ByteArrayInputStream(byteArray)
        return GZIPInputStream(byteStream)
            .use(HttpClient::readAllBytes)
    }

    private fun parseCharset(contentType: String?, default: Charset): Charset {
        if (contentType == null) return default

        val parts = contentType.split(";")
        for (part in parts.drop(1)) {
            val trimmed = part.trim()
            if (trimmed.startsWith("charset=", ignoreCase = true)) {
                val charsetName = trimmed.substringAfter("=")
                return try {
                    Charset.forName(charsetName)
                } catch (e: Exception) {
                    default // fallback if unknown/unsupported
                }
            }
        }
        return default
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HttpResponse

        if (statusCode != other.statusCode) return false
        if (url.toURI() != other.url.toURI()) return false // URL.equals can be slow
        if (message != other.message) return false
        if (headers != other.headers) return false
        if (body != null) {
            if (other.body == null) return false
            if (!body.contentEquals(other.body)) return false
        } else if (other.body != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + url.toURI().hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }
}