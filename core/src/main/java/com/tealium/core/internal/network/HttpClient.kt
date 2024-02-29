package com.tealium.core.internal.network

import com.tealium.core.BuildConfig
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.Cancelled
import com.tealium.core.api.network.Failure
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.HttpResponse
import com.tealium.core.api.network.IOError
import com.tealium.core.api.network.Interceptor
import com.tealium.core.api.network.NetworkClient
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.Non200Error
import com.tealium.core.api.network.RetryAfterDelay
import com.tealium.core.api.network.RetryAfterEvent
import com.tealium.core.api.network.Success
import com.tealium.core.api.network.UnexpectedError
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.DisposableContainer
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.addTo
import com.tealium.core.internal.observables.AsyncSubscription
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
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
    private val tealiumExecutor: ExecutorService,
    private val networkExecutor: ScheduledExecutorService,
    internal val interceptors: MutableList<Interceptor> = mutableListOf()
) : NetworkClient {

    override fun sendRequest(
        request: HttpRequest,
        completion: (NetworkResult) -> Unit
    ): Disposable {
        return sendRetryableRequest(request, 0) { result ->
            completion(result)
        }
    }

    private fun sendRetryableRequest(
        request: HttpRequest,
        retryCount: Int,
        completion: (NetworkResult) -> Unit
    ): Disposable {
        val disposableContainer = DisposableContainer()
        send(request, networkExecutor, tealiumExecutor) { result ->
            notifyInterceptors(request, result)
            processInterceptorsForDelay(request, result, retryCount) { shouldRetry ->
                if (disposableContainer.isDisposed) {
                    completion(Failure(Cancelled))
                    return@processInterceptorsForDelay
                }

                if (shouldRetry) {
                    val newRetryCount = retryCount + 1
                    sendRetryableRequest(request, newRetryCount, completion)
                        .addTo(disposableContainer)
                } else {
                    completion(result)
                }
            }
        }

        return AsyncSubscription(tealiumExecutor, disposableContainer)
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
                        delayRequest(policy.interval, request) {
                            shouldRetry(true)
                        }
                    }

                    is RetryAfterEvent<*> -> {
                        delayRequest(policy.event, request) {
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

    private fun delayRequest(interval: Long, request: HttpRequest, completion: () -> Unit) {
        logger.debug?.log(BuildConfig.TAG, "Delaying request: $request")
        networkExecutor.schedule({
            logger.debug?.log(BuildConfig.TAG, "End delay request: $request")
            completion()
        }, interval, TimeUnit.MILLISECONDS)
    }

    private fun <T> delayRequest(
        event: Observable<T>,
        request: HttpRequest,
        completion: () -> Unit
    ) {
        logger.debug?.log(BuildConfig.TAG, "Delaying request: $request")
        event.take(1)
            .subscribe {
                logger.debug?.log(BuildConfig.TAG, "End delay request: $request")
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
        private fun send(request: HttpRequest, executeOn: ExecutorService, resumeOn: ExecutorService,  completion: (NetworkResult) -> Unit): Future<*> {
            return executeOn.submit {
                val result = executeRequest(request)
                resumeOn.submit {
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
                connection = URL(request.url).openConnection() as HttpURLConnection
                with(connection) {
                    try {
                        requestMethod = request.method.value
                        request.headers?.forEach { (key, value) ->
                            setRequestProperty(key, value)
                        }

                        if (!requestProperties.containsKey("Accept-Encoding")) {
                            setRequestProperty("Accept-Encoding", "gzip")
                        }

                        if (request.body != null) {
                            doOutput = true

                            if (!requestProperties.containsKey("Content-Type")) {
                                setRequestProperty("Content-Type", "application/json")
                            }

                            val dataOutputStream = when (request.isGzip) {
                                true -> {
                                    setRequestProperty("Content-Encoding", "gzip")
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
                        val redirectedUrl = getHeaderField("Location")
                        if (redirectedUrl.isNullOrEmpty()) {
                            return@with Failure(
                                UnexpectedError(Exception("Received redirect response without a valid Location header"))
                            )
                        }
                    }

                    if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                        val contentEncoding = getHeaderField("Content-Encoding")
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
