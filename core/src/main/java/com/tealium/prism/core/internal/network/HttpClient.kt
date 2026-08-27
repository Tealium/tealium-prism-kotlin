package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfErrorEnabled
import com.tealium.prism.core.api.logger.logIfTraceEnabled
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.HttpRequest.Headers
import com.tealium.prism.core.api.network.HttpResponse
import com.tealium.prism.core.api.network.Interceptor
import com.tealium.prism.core.api.network.NetworkClient
import com.tealium.prism.core.api.network.NetworkException
import com.tealium.prism.core.api.network.NetworkException.CancelledException
import com.tealium.prism.core.api.network.NetworkException.Non200Exception
import com.tealium.prism.core.api.network.NetworkException.UnexpectedException
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Failure
import com.tealium.prism.core.api.network.NetworkResult.Success
import com.tealium.prism.core.api.network.RetryPolicy.RetryAfterDelay
import com.tealium.prism.core.api.network.RetryPolicy.RetryAfterEvent
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.pubsub.AsyncDisposableContainer
import com.tealium.prism.core.internal.pubsub.CompletedDisposable
import com.tealium.prism.core.api.pubsub.addTo
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

/**
 * Represents a network client responsible for sending HTTP requests and handling responses.
 * It supports interceptors for modifying requests and processing responses.
 *
 * @property interceptors The list of interceptors to be applied to the requests.
 */
class HttpClient(
    private val logger: Logger,
    private val tealiumScheduler: Scheduler,
    private val networkScheduler: Scheduler,
    internal val interceptors: MutableList<Interceptor> = mutableListOf()
) : NetworkClient {

    override fun sendRequest(
        request: HttpRequest,
        completion: Callback<NetworkResult>
    ): Disposable {
        logger.logIfTraceEnabled(LogCategory.HTTP_CLIENT) {
            "Sending request ${request.url}"
        }

        return sendRetryableRequest(request, 0) { result ->
            logResult(request, result)

            completion.onComplete(result)
        }
    }

    override fun sendRequest(
        request: HttpRequest.Builder,
        completion: Callback<NetworkResult>
    ): Disposable {
        val built = try {
            logger.logIfTraceEnabled(LogCategory.HTTP_CLIENT) {
                "Building request\n${request.description()}"
            }

            val httpRequest = request.build()

            logger.logIfTraceEnabled(LogCategory.HTTP_CLIENT) {
                "Built request $httpRequest"
            }

            httpRequest
        } catch (e: MalformedURLException) {
            logger.logIfErrorEnabled(LogCategory.HTTP_CLIENT) {
                "Failed to build request (malformed URL)"
            }

            completion.onComplete(Failure(UnexpectedException(e)))
            return CompletedDisposable
        }

        return sendRequest(built, completion)
    }

    /**
     * Logs the outcome of a request as two statements.
     *
     * The first is a concise summary of the result, logged at the result's [logLevel] so that a
     * failure is visible at the default log level.
     *
     * The second details the response and is always logged at [LogLevel.TRACE], as it includes the
     * response headers and body. Both can be large, and the headers can carry credentials or session
     * identifiers (e.g. `Set-Cookie`, `WWW-Authenticate`), so they are kept off the higher log levels
     * and only written where they have been explicitly opted in to.
     *
     * Note. only the summary reaches an active trace session, as a trace tracks the first
     * [LogLevel.ERROR] per log category; the detail statement is [LogLevel.TRACE] so it never leaves
     * the device.
     */
    private fun logResult(request: HttpRequest, result: NetworkResult) {
        logger.log(result.logLevel(), LogCategory.HTTP_CLIENT) {
            "Completed request ${request.url} with $result"
        }

        logger.logIfTraceEnabled(LogCategory.HTTP_CLIENT) {
            val message = StringBuilder("Response for request ${request.url}")

            result.httpResponseOrNull()?.let { response ->
                message.appendLine()
                    .appendLine("Headers: ${response.headers}")

                // reading the body can fail, e.g. mismatched content-encoding; never fail a log statement
                val body = runCatching { response.bodyText() }.getOrNull()
                if (!body.isNullOrEmpty()) {
                    message.appendLine("Body: $body")
                }
            } ?: message.appendLine().appendLine("No response was received")

            message.toString()
        }
    }

    private fun sendRetryableRequest(
        request: HttpRequest,
        retryCount: Int,
        completion: (NetworkResult) -> Unit
    ): Disposable {
        val disposableContainer = AsyncDisposableContainer(tealiumScheduler)
        send(request, networkScheduler, tealiumScheduler) { result ->
            notifyInterceptors(request, result)
            processInterceptorsForDelay(request, result, retryCount) { shouldRetry ->
                if (disposableContainer.isDisposed) {
                    completion(Failure(CancelledException))
                    return@processInterceptorsForDelay
                }

                if (shouldRetry) {
                    val newRetryCount = retryCount + 1
                    logger.logIfTraceEnabled(LogCategory.HTTP_CLIENT) {
                        "Retrying request ${request.url} Retry count: $newRetryCount"
                    }
                    sendRetryableRequest(request, newRetryCount, completion)
                        .addTo(disposableContainer)
                } else {
                    completion(result)
                }
            }
        }

        return disposableContainer
    }

    private fun notifyInterceptors(request: HttpRequest, result: NetworkResult) {
        interceptors.forEach { interceptor ->
            interceptor.didComplete(request, result)
        }
    }

    /**
     * Iterate through interceptors and process them specifically for determining delay behavior
     */
    internal fun processInterceptorsForDelay(
        request: HttpRequest,
        result: NetworkResult,
        retryCount: Int,
        shouldRetry: (Boolean) -> Unit
    ) {
        interceptors.reversed().forEach { interceptor ->
            val policy = interceptor.shouldRetry(request, result, retryCount)
            if (policy.shouldRetry()) {
                when (policy) {
                    is RetryAfterDelay -> {
                        delayRequest(policy.interval) {
                            shouldRetry(true)
                        }
                    }

                    is RetryAfterEvent<*> -> {
                        delayRequest(policy.event) {
                            shouldRetry(true)
                        }
                    }

                    else -> {
                        /** continue checking interceptors **/
                    }
                }
                return
            }
        }
        shouldRetry(false)
    }

    private fun delayRequest(interval: Long, completion: () -> Unit) {
        tealiumScheduler.schedule(TimeFrame(interval, TimeUnit.MILLISECONDS)) {
            completion()
        }
    }

    private fun <T> delayRequest(
        event: Observable<T>,
        completion: () -> Unit
    ) {
        event.take(1)
            .subscribe {
                completion()
            }
    }

    override fun addInterceptor(interceptor: Interceptor) {
        interceptors.add(interceptor)
    }

    override fun removeInterceptor(interceptor: Interceptor) {
        interceptors.remove(interceptor)
    }

    companion object {
        private const val DEFAULT_TIMEOUT = 30_000

        /**
         * Returns the [HttpResponse] for this result, where one is available.
         *
         * A failure only carries response data where a response was received before the request
         * failed, and could be read.
         */
        private fun NetworkResult.httpResponseOrNull(): HttpResponse? = when (this) {
            is Success -> httpResponse
            is Failure -> networkException.httpResponse
        }

        /**
         * The [LogLevel] to summarize this result at.
         *
         * Failures are logged at [LogLevel.ERROR] so that they are visible at the default log level,
         * with the exception of a `304 Not Modified`, which is the expected outcome of a conditional
         * request for an unchanged resource rather than a problem to be diagnosed. Successes are the
         * high volume path, so they are logged at [LogLevel.DEBUG].
         */
        private fun NetworkResult.logLevel(): LogLevel = when {
            this is Success -> LogLevel.DEBUG
            this is Failure && networkException.let {
                it is Non200Exception && it.statusCode == HttpURLConnection.HTTP_NOT_MODIFIED
            } -> LogLevel.DEBUG

            else -> LogLevel.ERROR
        }

        /**
         * Submits the job onto the background queue,
         */
        private fun send(
            request: HttpRequest,
            executeOn: Scheduler,
            resumeOn: Scheduler,
            completion: (NetworkResult) -> Unit
        ) {
            return executeOn.execute {
                val result = executeRequest(request)
                resumeOn.execute {
                    completion(result)
                }
            }
        }

        /**
         * Blocking execution of an HTTP request - it's not advised to call this method directly,
         * and [send] should be preferred to ensure control of the Thread used when making the request.
         */
        private fun executeRequest(request: HttpRequest): NetworkResult {
            var connection: HttpURLConnection? = null
            return try {
                connection = request.url.openConnection() as HttpURLConnection
                connection.connectTimeout = DEFAULT_TIMEOUT
                connection.readTimeout = DEFAULT_TIMEOUT
                with(connection) {
                    requestMethod = request.method.value
                    request.headers.forEach { (key, value) ->
                        setRequestProperty(key, value)
                    }

                    if (!requestProperties.containsKey(Headers.ACCEPT_ENCODING)) {
                        setRequestProperty(Headers.ACCEPT_ENCODING, "gzip")
                    }

                    if (request.body != null) {
                        doOutput = true

                        if (!requestProperties.containsKey(Headers.CONTENT_TYPE)) {
                            setRequestProperty(Headers.CONTENT_TYPE, "application/json")
                        }

                        val dataOutputStream = when (request.isGzip) {
                            true -> {
                                DataOutputStream(GZIPOutputStream(outputStream))
                            }

                            false -> {
                                DataOutputStream(outputStream)
                            }
                        }
                        dataOutputStream.write(request.body.toByteArray(Charsets.UTF_8))
                        dataOutputStream.flush()
                        dataOutputStream.close()
                    }

                    if (HttpURLConnection.HTTP_MOVED_PERM == responseCode
                        || HttpURLConnection.HTTP_MOVED_TEMP == responseCode
                        || HttpURLConnection.HTTP_SEE_OTHER == responseCode
                    ) {
                        val redirectedUrl = getHeaderField(Headers.LOCATION)
                        if (redirectedUrl.isNullOrEmpty()) {
                            return@with Failure(
                                UnexpectedException(
                                    Exception("Received redirect response without a valid Location header"),
                                    toHttpResponse(readBodyLeniently())
                                )
                            )
                        }
                    }

                    if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                        val body = try {
                            inputStream.use(::readAllBytes)
                        } catch (e: IOException) {
                            return@with Failure(
                                NetworkException.NetworkIOException(e, toHttpResponse(null))
                            )
                        }
                        return@with Success(toHttpResponse(body))
                    } else {
                        // Non200Status Error
                        return@with Failure(
                            Non200Exception(responseCode, toHttpResponse(readBodyLeniently()))
                        )
                    }
                }
            } catch (e: IOException) {
                Failure(NetworkException.NetworkIOException(e))
            } catch (e: Exception) {
                Failure(UnexpectedException(e))
            } finally {
                connection?.disconnect()
            }
        }

        /**
         * The response received on this connection, along with the given [body].
         */
        private fun HttpURLConnection.toHttpResponse(body: ByteArray?) =
            HttpResponse(url, responseCode, responseMessage, headerFields, body)

        /**
         * Leniently reads the body of a failed response, so that any body returned by the server is
         * available for inspection.
         *
         * A 4XX or 5XX body arrives on the [HttpURLConnection.getErrorStream]. Below that - notably
         * a redirect that could not be followed - it arrives on the
         * [HttpURLConnection.getInputStream] instead, as for a successful response.
         *
         * Reading the body is best-effort only; any failure here is ignored so that the reported
         * cause of the failure remains the response itself, whose status code, message and headers
         * are still useful for diagnosis without a body.
         *
         * @return the response body, or `null` if there was none, or it could not be read
         */
        private fun HttpURLConnection.readBodyLeniently(): ByteArray? = runCatching {
            val body = if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                errorStream
            } else {
                inputStream
            }

            body?.use(::readAllBytes)
        }.getOrNull()

        fun readAllBytes(inputStream: InputStream): ByteArray {
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            return output.toByteArray()
        }
    }
}
