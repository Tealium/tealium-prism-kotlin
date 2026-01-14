package com.tealium.prism.core.api.network

import com.tealium.prism.core.api.data.DataObject
import java.net.MalformedURLException
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
class HttpRequest private constructor(
    val url: URL,
    val method: HttpMethod,
    val body: String?,
    val headers: Map<String, String>
) {

    val isGzip: Boolean
        get() = "gzip" == headers[Headers.CONTENT_ENCODING]

    val etag: String?
        get() = headers[Headers.ETAG]

    override fun toString(): String {
        return "HttpRequest(" +
                "url=" + url.toString() +
                ", method=" + method.value +
                ", body=" + (body ?: "") +
                ", headers=" + headers +
                ")"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HttpRequest

        if (url != other.url) return false
        if (method != other.method) return false
        if (body != other.body) return false
        if (headers != other.headers) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + (body?.hashCode() ?: 0)
        result = 31 * result + headers.hashCode()
        return result
    }

    /**
     * Builder helper class for constructing [HttpRequest]s.
     *
     * Although this class can be created directly, there are some additional helper methods to start
     * the builder process: [HttpRequest.get] and [HttpRequest.post] that will prepopulate the [Builder]
     * with the given parameters.
     *
     * @param url required destination; the [build] method will throw if this is invalid
     * @param method required http method for making the request
     *
     * @see HttpRequest.get
     * @see HttpRequest.post
     */
    class Builder private constructor(
        private val urlSupplier: () -> URL,
        private val urlString: String,
        private val method: HttpMethod
    ) {

        constructor(url: URL, method: HttpMethod): this({ url }, url.toString(), method)
        constructor(url: String, method: HttpMethod): this({ URL(url) }, url, method)

        private val headers = mutableMapOf<String, String>()
        private var body: String? = null
        private var shouldGzip: Boolean? = null
        private var etag: String? = null

        /**
         * Sets an HTTP header for the outgoing request.
         *
         * All [field]s are lowercased before storing for consistency - there are also some commonly
         * used header constants available at [Headers].
         *
         * @param field The header field to set, e.g. "Content-Encoding"
         * @param value The value to set for the given [field]
         *
         * @see Headers
         */
        fun header(field: String, value: String): Builder = apply {
            headers[field.lowercase()] = value
        }

        /**
         * Sets multiple HTTP headers at once for the outgoing request.
         *
         * All header field names are lowercased before storing for consistency.
         *
         * @param additionalHeaders Optional map of additional headers to add to the request
         * @return The builder instance for method chaining
         */
        fun additionalHeaders(additionalHeaders: Map<String, String>?): Builder = apply {
            additionalHeaders?.forEach { (key, value) ->
                header(key, value)
            }
        }

        /**
         * Sets the request body that needs to be sent.
         *
         * @param body The request body to send
         */
        fun body(body: String?): Builder = apply {
            this.body = body
        }

        /**
         * Sets the request body that needs to be sent.
         *
         * @param dataObject The request body to send
         */
        fun body(dataObject: DataObject?): Builder = body(dataObject?.toString())

        /**
         * Sets whether or not the outbound request will be GZipped prior to sending.
         *
         * Note. the `Content-Encoding` header will be automatically added during the call to [build],
         * so will overwrite any similar headers set via the [header] method.
         *
         * @param shouldGzip `true` if the content should be gzipped; else `false`
         */
        fun gzip(shouldGzip: Boolean): Builder = apply {
            this.shouldGzip = shouldGzip
        }

        /**
         * Sets an optional etag value for a previously cached value.
         *
         * Note. the `ETag` header will be automatically added during the call to [build], so will
         * overwrite any similar headers set via the [header] method.
         */
        fun etag(etag: String?): Builder = apply {
            this.etag = etag
        }

        /**
         * Constructs the [HttpRequest] using the parameters given to the builder.
         *
         * If the provided URL is invalid/malformed, then an exception will be thrown at this point
         * to fail early.
         *
         * Gzip and etag headers are also added at this point, so will overwrite any matching headers
         * set on the builder via the [header] method
         *
         * @return the validated [HttpRequest]
         */
        @Throws(MalformedURLException::class)
        fun build(): HttpRequest {
            shouldGzip?.let {
                header(Headers.CONTENT_ENCODING, "gzip")
            }

            etag?.let {
                header(Headers.ETAG, it)
            }

            return HttpRequest(urlSupplier.invoke(), method, body, headers.toMap())
        }

        fun description() : String {
            val stringBuilder = StringBuilder()
                .appendLine("${method.value}: $urlString")
                .appendLine("Headers: $headers")

            body?.let {
                stringBuilder.appendLine("Body: $it")
            }

            return stringBuilder.toString()
        }
    }


    companion object {

        /**
         * Utility method for building a simple POST request.
         *
         * @param destination The url to be POSTed to
         * @param payload The body of the POST request
         * @param gzip true to gzip compress the [payload]; otherwise false
         */
        @JvmStatic
        @Throws(MalformedURLException::class)
        fun post(destination: String, payload: String): Builder {
            return Builder(destination, HttpMethod.Post)
                .body(payload)
        }

        /**
         * Utility method for building a simple GET request.
         *
         * @param destination the url to GET
         * @param etag optional etag allowing the server to efficiently return the appropriate
         * response if the data requested hasn't changed.
         */
        @JvmStatic
        @Throws(MalformedURLException::class)
        fun get(destination: String, etag: String? = null): Builder {
            return Builder(destination, HttpMethod.Get)
                .etag(etag)
        }

        /**
         * Utility method for building a simple POST request.
         *
         * @param destination The url to be POSTed to
         * @param payload The body of the POST request
         * @param gzip true to gzip compress the [payload]; otherwise false
         */
        @JvmStatic
        fun post(destination: URL, payload: String): Builder {
            return Builder(destination, HttpMethod.Post)
                .body(payload)
        }

        /**
         * Utility method for building a simple GET request.
         *
         * @param destination the url to GET
         * @param etag optional etag allowing the server to efficiently return the appropriate
         * response if the data requested hasn't changed.
         */
        @JvmStatic
        fun get(destination: URL, etag: String? = null): Builder {
            return Builder(destination, HttpMethod.Get)
                .etag(etag)
        }
    }

    /**
     * Object containing some constants for commonly used HTTP Headers
     */
    object Headers {
        const val ACCEPT_ENCODING = "accept-encoding"
        const val CONTENT_ENCODING = "content-encoding"
        const val CONTENT_TYPE = "content-type"
        const val ETAG = "etag"
        const val LOCATION = "location"
    }
}
