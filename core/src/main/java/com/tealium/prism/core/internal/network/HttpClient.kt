package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfTraceEnabled
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumCallback
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
import com.tealium.prism.core.internal.pubsub.addTo
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
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
        completion: TealiumCallback<NetworkResult>
    ): Disposable {
        logger.logIfTraceEnabled(LogCategory.HTTP_CLIENT) {
            "Sending request ${request.url}"
        }

        return sendRetryableRequest(request, 0) { result ->
            logger.log(
                if (result is Success) LogLevel.TRACE else LogLevel.ERROR,
                LogCategory.HTTP_CLIENT,
                "Completed request %s with %s",
                request.url, result
            )

            completion.onComplete(result)
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

            shouldRetry(false)
        }
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
                                UnexpectedException(Exception("Received redirect response without a valid Location header"))
                            )
                        }
                    }

                    if (responseCode >= HttpURLConnection.HTTP_OK && responseCode < HttpURLConnection.HTTP_MULT_CHOICE) {
                        val body: ByteArray = inputStream.use(::readAllBytes)

                        return@with Success(
                            HttpResponse(url, responseCode, responseMessage, headerFields, body)
                        )
                    } else {
                        // Non200Status Error
                        return@with Failure(Non200Exception(responseCode))
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
