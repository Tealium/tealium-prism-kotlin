package com.tealium.core.internal.network

import android.util.Log
import com.tealium.core.BuildConfig
import com.tealium.core.api.network.AfterDelay
import com.tealium.core.api.network.AfterEvent
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Represents a network client responsible for sending HTTP requests and handling responses.
 * It supports interceptors for modifying requests and processing responses.
 *
 * @property interceptors The list of interceptors to be applied to the requests.
 */
class HttpClient(
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    internal val interceptors: MutableList<Interceptor> = mutableListOf()
) : NetworkClient {

    override fun sendRequestAsync(
        request: HttpRequest
    ): Deferred<NetworkResult> {
        return ioScope.async {
            try {
                return@async processRequest(request, 0)
            } catch (ex: CancellationException) {
                Log.d(BuildConfig.TAG, "Request canceled: $request")
                return@async Failure(Cancelled)
            }
        }.ensureSafeDeferred(Failure(Cancelled))
    }

    private suspend fun processRequest(request: HttpRequest, retryCount: Int): NetworkResult =
        coroutineScope {
            if (!this.isActive) {
                return@coroutineScope Failure(Cancelled)
            }

            processInterceptorsForDelay(request)

            val result = send(request)
            notifyResponseResult(request, result)

            return@coroutineScope when (processInterceptorsForRetry(request, result, retryCount)) {
                false -> result
                else -> processRequest(request, retryCount + 1)
            }
        }

    private suspend fun send(request: HttpRequest): NetworkResult = coroutineScope {
        lateinit var connection: HttpURLConnection
        return@coroutineScope try {
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
                    Failure(UnexpectedError(e))
                }

                if (HttpURLConnection.HTTP_MOVED_PERM == responseCode
                    || HttpURLConnection.HTTP_MOVED_TEMP == responseCode
                    || HttpURLConnection.HTTP_SEE_OTHER == responseCode
                ) {
                    val redirectedUrl = getHeaderField("Location")
                    if (redirectedUrl.isNullOrEmpty()) {
                        return@coroutineScope Failure(
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

                    Success(
                        HttpResponse(url, responseCode, responseMessage, headerFields, body)
                    )
                } else {
                    // Non200Status Error
                    Failure(Non200Error(responseCode))
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

    private fun notifyResponseResult(request: HttpRequest, result: NetworkResult) {
        interceptors.forEach { interceptor ->
            interceptor.didComplete(request, result)
        }
    }

    /**
     * Iterate through interceptors and process them specifically for determining delay behavior
     */
    internal suspend fun processInterceptorsForDelay(request: HttpRequest) {
        interceptors.reversed().forEach { interceptor ->
            val policy = interceptor.shouldDelay(request)
            if (policy.shouldDelay()) {
                when (policy) {
                    is AfterDelay -> {
                        delayRequest(policy.interval, request)
                        return
                    }
                    is AfterEvent<*> -> {
                        delayRequest(policy.event, request)
                        return
                    }
                    else -> {
                        /** continue checking interceptors **/
                    }
                }
            }
        }
    }

    private suspend fun delayRequest(interval: Long, request: HttpRequest) {
        Log.d(BuildConfig.TAG, "Delaying request: $request")
        delay(interval)
        Log.d(BuildConfig.TAG, "End delay request: $request")
    }

    private suspend fun <T> delayRequest(flow: Flow<T>, request: HttpRequest) {
        Log.d(BuildConfig.TAG, "Delaying request: $request")
        flow.take(1)
            .onEach {
                Log.d(BuildConfig.TAG, "Received delay continuation: $request")
            }
            .collect()
        Log.d(BuildConfig.TAG, "End delay request: $request")
    }

    /**
     * Iterate through interceptors and process them specifically for determining retry behavior
     */
    internal suspend fun processInterceptorsForRetry(
        request: HttpRequest,
        result: NetworkResult,
        retryCount: Int
    ): Boolean {
        interceptors.reversed().forEach { interceptor ->
            val retryPolicy = interceptor.shouldRetry(request, result, retryCount)
            if (retryPolicy.shouldRetry()) {
                when (retryPolicy) {
                    is RetryAfterDelay -> {
                        delayRequest(retryPolicy.interval, request)
                        return true
                    }
                    is RetryAfterEvent<*> -> {
                        // delayByEvent
                        return true
                    }
                    else -> {
                        /** continue checking interceptors **/
                    }
                }
            }
        }

        return false
    }

    override fun addInterceptor(interceptor: Interceptor) {
        interceptors.add(interceptor)
    }

    override fun removeInterceptor(interceptor: Interceptor) {
        interceptors.remove(interceptor)
    }
}