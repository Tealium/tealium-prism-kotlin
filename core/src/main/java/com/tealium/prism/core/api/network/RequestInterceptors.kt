package com.tealium.prism.core.api.network

import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.prism.core.api.misc.TimeFrameUtils.seconds
import com.tealium.prism.core.internal.network.ConnectivityRetryInterceptor
import com.tealium.prism.core.internal.network.RetryableErrorInterceptor

/**
 * Utility object for getting the built-in [RequestInterceptor]s, to add to a client derived with
 * [NetworkClient.newClient].
 *
 * @see RequestInterceptor
 */
object RequestInterceptors {

    /**
     * Creates an interceptor that retries retryable failures (see
     * [NetworkException.isRetryable]), for example HTTP 429 and 503 or a lost connection,
     * with a full-jitter exponential backoff, and decides when to give up.
     *
     *  - The `n`-th retry in a row proposed by this interceptor, counting from 0, waits a
     *  random delay between 0 and `min(maxDelay, base * 2^n)`. `base` is
     *  [throttlingBaseDelay] for HTTP 429 and [transientBaseDelay] otherwise.
     *  - A `Retry-After` header is a minimum wait: the delay is the larger of the header and
     *  the backoff. The header can be a number of seconds or an HTTP date in the IMF-fixdate
     *  format, measured against the response's `Date` header when present so a skewed device
     *  clock doesn't matter. Other formats are ignored. For a header above [maxDelay], the
     *  interceptor either stops proposing a retry or waits for it in full, depending on
     *  [retryAfterAboveMaxDelay].
     *  - [maxAttempts] counts consecutive attempts, including the one that just failed. The
     *  count, and with it the backoff, restarts when another interceptor proposed the last
     *  retry, for example after waiting for connectivity. To limit the total retries of a
     *  request, derive the client with a `maxRetryCount` (see [NetworkClient.newClient]).
     *
     * @param maxAttempts The maximum number of consecutive attempts, including the initial one;
     * `null` means there is no cap.
     * @param transientBaseDelay The base delay for retryable failures other than HTTP 429.
     * @param throttlingBaseDelay The base delay for HTTP 429 responses.
     * @param maxDelay The longest backoff delay. A `Retry-After` up to this long is waited as a
     * minimum; a longer one is handled per [retryAfterAboveMaxDelay].
     * @param retryAfterAboveMaxDelay What to do when a `Retry-After` header asks for longer
     * than [maxDelay]: [RetryAfterAboveMaxDelay.GIVE_UP] stops proposing a retry, so another
     * interceptor may still retry, and [RetryAfterAboveMaxDelay.WAIT] waits for the full
     * header, with no upper bound.
     * @return A new [RequestInterceptor]; it keeps no state, so it can be shared between
     * clients.
     */
    @JvmStatic
    @JvmOverloads
    fun retryWithBackoff(
        maxAttempts: Int? = 3,
        transientBaseDelay: TimeFrame = 100.milliseconds,
        throttlingBaseDelay: TimeFrame = 1.seconds,
        maxDelay: TimeFrame = 20.seconds,
        retryAfterAboveMaxDelay: RetryAfterAboveMaxDelay = RetryAfterAboveMaxDelay.GIVE_UP
    ): RequestInterceptor = RetryableErrorInterceptor(
        maxAttempts, transientBaseDelay, throttlingBaseDelay, maxDelay, retryAfterAboveMaxDelay
    )

    /**
     * Creates an interceptor that, while the device is not connected, retries a retryable
     * failure (see [NetworkException.isRetryable]) once connectivity returns.
     *
     * For a retryable failure while [Connectivity.connectionStatus] is
     * [Connectivity.Status.NotConnected], the interceptor returns a [RetryPolicy.AfterEvent]
     * that fires the next time the status is no longer [Connectivity.Status.NotConnected].
     * Otherwise it returns [RetryPolicy.DoNotRetry], so it is meant to be combined with
     * [retryWithBackoff].
     *
     * While the status is [Connectivity.Status.Unknown] a failure is not held back waiting for
     * connectivity, as the SDK does not hold back sending for that status.
     *
     * @param connectivity The [Connectivity] used to know when the device is connected again.
     * @return A new [RequestInterceptor]; it can be shared between clients.
     */
    @JvmStatic
    fun retryWhenConnected(connectivity: Connectivity): RequestInterceptor =
        ConnectivityRetryInterceptor(connectivity)
}
