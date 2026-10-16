package com.tealium.prism.core.internal.network

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * A delay, in milliseconds, computed from the number of retries already made. Useful to understand
 * how long to wait before retrying.
 *
 * @param calculate Computes the delay, in milliseconds, for the given retry count: the number of
 * retries already made, so `0` is the first retry.
 */
internal class Backoff(
    private val calculate: (retryCount: Int) -> Long
) {

    /**
     * Returns the delay, in milliseconds, to wait before the retry with the given [retryCount].
     *
     * @param retryCount The number of retries already made; `0` is the first retry.
     */
    fun delay(retryCount: Int): Long = calculate(retryCount)

    /**
     * Returns a [Backoff] whose delay is [transform] applied to this instance's delay.
     */
    fun map(transform: (Long) -> Long): Backoff =
        Backoff { retryCount -> transform(calculate(retryCount)) }

    companion object {
        /**
         * The greatest exponent applied to the multiplier. Higher retry counts reuse it, so that the
         * computation cannot overflow however many retries are made.
         */
        internal const val MAX_EXPONENT = 30

        /**
         * A delay that grows exponentially with the retry count.
         *
         * For retry count `n` the delay is `min(maximum, initialDelay * multiplier^min(max(0, n), 30))`:
         * the first retry (`n` = 0) waits [initialDelay] and every following one multiplies it by
         * [multiplier], until [maximum] is reached.
         *
         * The delay is always between `0` and [maximum]: a result that overflows saturates to
         * [maximum], and a non-positive [initialDelay], [maximum] or computed delay counts as `0`.
         *
         * @param initialDelay The delay of the first retry, in milliseconds.
         * @param multiplier The factor applied to the delay at every following retry.
         * @param maximum The greatest delay, in milliseconds.
         */
        fun exponential(
            initialDelay: Long,
            multiplier: Double = 2.0,
            maximum: Long
        ): Backoff = Backoff { retryCount ->
            exponentialDelay(initialDelay, multiplier, maximum, retryCount)
        }

        /**
         * A random delay between `0` and the delay of [exponential], both inclusive, also known as
         * "full jitter".
         *
         * [random] is not invoked when the upper bound is `0`.
         *
         * @param initialDelay The upper bound of the first retry's delay, in milliseconds.
         * @param multiplier The factor applied to the upper bound at every following retry.
         * @param maximum The greatest upper bound, in milliseconds.
         * @param random Returns a random value between `0` and the given bound, both inclusive;
         * injectable for tests.
         */
        fun jitteredExponential(
            initialDelay: Long,
            multiplier: Double = 2.0,
            maximum: Long,
            random: (bound: Long) -> Long = ::randomUpTo
        ): Backoff = exponential(initialDelay, multiplier, maximum)
            .map { upperBound -> if (upperBound > 0) random(upperBound) else 0 }

        private fun exponentialDelay(
            initialDelay: Long,
            multiplier: Double,
            maximum: Long,
            retryCount: Int
        ): Long {
            if (initialDelay <= 0 || maximum <= 0) return 0

            val exponent = min(retryCount.coerceAtLeast(0), MAX_EXPONENT)
            val factor = multiplier.pow(exponent)
            if (factor.isNaN() || factor <= 0) return 0
            // avoids the precision loss of a Long -> Double round trip for large initial delays
            if (factor == 1.0) return min(initialDelay, maximum)

            val delay = initialDelay * factor
            if (delay >= maximum.toDouble()) return maximum

            return delay.toLong().coerceIn(0, maximum)
        }

        /**
         * Returns a uniformly distributed random value in `0..bound`, both inclusive.
         */
        private fun randomUpTo(bound: Long): Long =
            if (bound == Long.MAX_VALUE) {
                Random.nextLong() and Long.MAX_VALUE
            } else {
                Random.nextLong(bound + 1)
            }
    }
}
