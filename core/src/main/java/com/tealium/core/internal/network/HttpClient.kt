package com.tealium.core.internal.network

import com.tealium.core.api.logger.Logger
import com.tealium.core.api.logger.Logs
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.HttpRequest.Headers
import com.tealium.core.api.network.HttpResponse
import com.tealium.core.api.network.Interceptor
import com.tealium.core.api.network.NetworkClient
import com.tealium.core.api.network.NetworkError.Cancelled
import com.tealium.core.api.network.NetworkError.IOError
import com.tealium.core.api.network.NetworkError.Non200Error
import com.tealium.core.api.network.NetworkError.UnexpectedError
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.NetworkResult.Failure
import com.tealium.core.api.network.NetworkResult.Success
import com.tealium.core.api.network.RetryPolicy.RetryAfterDelay
import com.tealium.core.api.network.RetryPolicy.RetryAfterEvent
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.internal.logger.LogCategory
import com.tealium.core.internal.pubsub.AsyncSubscription
import com.tealium.core.internal.pubsub.DisposableContainer
import com.tealium.core.internal.pubsub.addTo
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
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
        completion: (NetworkResult) -> Unit
    ): Disposable {
        logger.trace?.log(LogCategory.HTTP_CLIENT, "Sending request ${request.url} ${request.body}")
        return sendRetryableRequest(request, 0) { result ->
            val resultLogger: Logs? = when (result) {
                is Success -> logger.trace
                is Failure -> logger.error
            }
            resultLogger?.log(
                LogCategory.HTTP_CLIENT,
                "Completed request ${request.url} ${request.body} with $result"
            )
            completion(result)
        }
    }

    private fun sendRetryableRequest(
        request: HttpRequest,
        retryCount: Int,
        completion: (NetworkResult) -> Unit
    ): Disposable {
        val disposableContainer = DisposableContainer()
        send(request, networkScheduler, tealiumScheduler) { result ->
            notifyInterceptors(request, result)
            processInterceptorsForDelay(request, result, retryCount) { shouldRetry ->
                if (disposableContainer.isDisposed) {
                    completion(Failure(Cancelled))
                    return@processInterceptorsForDelay
                }

                if (shouldRetry) {
                    val newRetryCount = retryCount + 1
                    logger.trace?.log(
                        LogCategory.HTTP_CLIENT,
                        "Retrying request ${request.url} ${request.body} Retry count: $newRetryCount"
                    )
                    sendRetryableRequest(request, newRetryCount, completion)
                        .addTo(disposableContainer)
                } else {
                    completion(result)
                }
            }
        }

        return AsyncSubscription(tealiumScheduler, disposableContainer)
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

            shouldRetry(false)
        }
    }

    private fun delayRequest(interval: Long, completion: () -> Unit) {
        networkScheduler.schedule(TimeFrame(interval, TimeUnit.MILLISECONDS)) {
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
            lateinit var connection: HttpURLConnection
            return try {
                connection = request.url.openConnection() as HttpURLConnection
                with(connection) {
                    try {
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


                    } catch (e: Exception) {
                        return@with Failure(UnexpectedError(e))
                    }

                    if (HttpURLConnection.HTTP_MOVED_PERM == responseCode
                        || HttpURLConnection.HTTP_MOVED_TEMP == responseCode
                        || HttpURLConnection.HTTP_SEE_OTHER == responseCode
                    ) {
                        val redirectedUrl = getHeaderField(Headers.LOCATION)
                        if (redirectedUrl.isNullOrEmpty()) {
                            return@with Failure(
                                UnexpectedError(Exception("Received redirect response without a valid Location header"))
                            )
                        }
                    }

                    if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                        val contentEncoding = contentEncoding
                        val body: String = if (contentEncoding == "gzip") {
                            GZIPInputStream(inputStream).bufferedReader().readText()
                        } else {
                            inputStream.bufferedReader().readText()
                        }

                        return@with Success(
                            HttpResponse(url, responseCode, responseMessage, headerFields, body)
                        )
                    } else {
                        // Non200Status Error
                        return@with Failure(Non200Error(responseCode))
                    }

                }
            } catch (e: IOException) {
                Failure(IOError(e))
            } catch (e: Exception) {
                Failure(UnexpectedError(e))
            } finally {
                connection.disconnect()
            }
        }
    }
}
