package com.tealium.core.api

import com.tealium.core.internal.persistence.getTimestamp
import java.util.concurrent.TimeUnit

/**
 * This class describes the expiration time of a given resource. Some fixed values are available
 * to tie the expiration to a given event.
 * [SESSION] will expire upon the start of a new session being created
 * [UNTIL_RESTART] will expire upon the next launch of the application
 * [FOREVER] will not expire
 *
 * For specific expiration timings, there are utility methods to handle future dates.
 * [afterDate]/[afterEpochTime]/[afterTimeUnit] will all produces [Expiry] values that will expire
 * at future times.
 *
 * It is accurate to the second.
 *
 */
sealed class Expiry {

    /**
     * Returns the expiry time as a [Long] for this [Expiry].
     * For special cases, this value will be negative; for specific times in the future, this value
     * will be positive.
     *
     * @return The expiry time associated with this [Expiry]
     */
    abstract fun expiryTime(): Long

    /**
     * Returns the time remaining in seconds for this [Expiry] before it will expire.
     *
     * For special cases, this value will be negative.
     * For specific times in the future, this value will be:
     *  - positive when not expired
     *  - negative when expired
     *
     * @return The expiry time associated with this [Expiry]
     */
    abstract fun timeRemaining(): Long

    private object Session : Expiry() {
        override fun expiryTime(): Long {
            return -2L
        }

        override fun timeRemaining(): Long {
            return -2L
        }
    }

    private object Forever : Expiry() {
        override fun expiryTime(): Long {
            return -1L
        }

        override fun timeRemaining(): Long {
            return -1L
        }
    }

    private object UntilRestart : Expiry() {
        override fun expiryTime(): Long {
            return -3L
        }

        override fun timeRemaining(): Long {
            return -3L
        }
    }

    private class After constructor(
        private val timeDifference: Long,
        private val creationTime: Long = getTimestamp()
    ) : Expiry() {

        override fun expiryTime(): Long {
            return creationTime + timeDifference
        }

        override fun timeRemaining(): Long {
            return creationTime + timeDifference - getTimestamp()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Expiry) return false

        return if (this === Session) {
            other === Session
        } else if (this === Forever) {
            other === Forever
        } else if (this === UntilRestart) {
            other === UntilRestart
        } else {
            this.expiryTime() == other.expiryTime()
        }
    }

    override fun hashCode(): Int {
        var result = 7
        val expiryTime = expiryTime()
        result = 31 * result + (expiryTime.xor(expiryTime.shr(32))).toInt()
        result = 31 * result + (if (isExpired(this)) 1 else 0)
        return result
    }

    companion object {

        /**
         * Specific [Expiry] case that should be tied the lifetime of the current session.
         */
        @JvmField
        val SESSION: Expiry = Session

        /**
         * Specific [Expiry] case that should never expire.
         */
        @JvmField
        val FOREVER: Expiry = Forever

        /**
         * Specific [Expiry] case that should be tied the lifetime of the application launch.
         */
        @JvmField
        val UNTIL_RESTART: Expiry = UntilRestart

        /**
         * @return Sum of the value in Seconds and the current system time in seconds to give a valid
         * epoch time in seconds.
         */
        @JvmStatic
        @JvmOverloads
        fun afterTimeUnit(value: Long, unit: TimeUnit, fromTime: Long = getTimestamp()): Expiry {
            val long = unit.toSeconds(value)
            return After(long, fromTime)
        }

        /**
         * @return Sum of the given values (converted to Seconds) and the current system time in
         * seconds to give a valid epoch time in seconds
         */
        @JvmStatic
        @JvmOverloads
        fun afterDate(
            days: Int,
            hours: Int,
            minutes: Int,
            seconds: Int,
            fromTime: Long = getTimestamp()
        ): Expiry {
            val long = TimeUnit.DAYS.toSeconds(days.toLong()) +
                    TimeUnit.HOURS.toSeconds(hours.toLong()) +
                    TimeUnit.MINUTES.toSeconds(minutes.toLong()) +
                    seconds.toLong()
            return After(long, fromTime)
        }

        /**
         * Allows you to set a specific Epoch time in seconds for which to expire.
         */
        @JvmStatic
        fun afterEpochTime(timeInSeconds: Long): Expiry {
            return After(timeInSeconds, 0L)
        }

        /**
         * Creates an [Expiry] from a [Long] value. Long is assumed to be the Epoch Time in seconds,
         * unless negative.
         */
        @JvmStatic
        fun fromLongValue(long: Long): Expiry {
            return when (long) {
                -3L -> UNTIL_RESTART
                -2L -> SESSION
                -1L -> FOREVER
                else -> {
                    afterEpochTime(long)
                }
            }
        }

        /**
         * Helper to determine whether an [Expiry] object is classed as expired.
         * Note. [FOREVER] and [SESSION] will always be false.
         */
        @JvmStatic
        fun isExpired(expiry: Expiry?): Boolean {
            return when (expiry) {
                null -> false
                is Forever -> false
                is Session -> false
                is UntilRestart -> false
                else -> {
                    expiry.timeRemaining() < 0
                }
            }
        }
    }
}
