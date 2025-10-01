package com.tealium.prism.core.api.misc

import java.util.concurrent.TimeUnit

/**
 * Class denoting a length of time that is easily convertible into any required time units.
 *
 * The class is [Comparable] for easy comparison. Although it should be noted that the comparison may
 * not be reliable when comparing very large units with very small units as the conversions are
 * taken from [TimeUnit] which will truncate limit conversions to [Long.MAX_VALUE]/[Long.MIN_VALUE]
 * in cases when a conversion would exceed the max/min value.
 */
data class TimeFrame(
    val number: Long,
    val unit: TimeUnit
) : Comparable<TimeFrame> {

    override fun compareTo(other: TimeFrame): Int {
        return if (unit == other.unit) {
            number.compareTo(other.number)
        } else if (unit > other.unit) {
            val thisNumberInOtherUnit = other.unit.convert(number, unit)
            thisNumberInOtherUnit.compareTo(other.number)
        } else {
            val otherNumberInThisUnit = unit.convert(other.number, other.unit)
            number.compareTo(otherNumberInThisUnit)
        }
    }
}

object TimeFrameUtils {
    /**
     * Returns a [TimeFrame] representing the number of nanoseconds given by the integer value
     *
     * ```kotlin
     * 10.nanoseconds // -> TimeFrame(10, TimeUnit.NANOSECONDS)
     * ```
     */
    @JvmStatic
    inline val Int.nanoseconds: TimeFrame
        get() = TimeFrame(toLong(), TimeUnit.NANOSECONDS)

    /**
     * Returns a [TimeFrame] representing the number of microseconds given by the integer value
     *
     * ```kotlin
     * 10.microseconds // -> TimeFrame(10, TimeUnit.MICROSECONDS)
     * ```
     */
    @JvmStatic
    inline val Int.microseconds: TimeFrame
        get() = TimeFrame(toLong(), TimeUnit.MICROSECONDS)

    /**
     * Returns a [TimeFrame] representing the number of milliseconds given by the integer value
     *
     * ```kotlin
     * 10.milliseconds // -> TimeFrame(10, TimeUnit.MILLISECONDS)
     * ```
     */
    @JvmStatic
    inline val Int.milliseconds: TimeFrame
        get() = TimeFrame(toLong(), TimeUnit.MILLISECONDS)

    /**
     * Returns a [TimeFrame] representing the number of seconds given by the integer value
     *
     * ```kotlin
     * 10.seconds // -> TimeFrame(10, TimeUnit.SECONDS)
     * ```
     */
    @JvmStatic
    inline val Int.seconds: TimeFrame
        get() = TimeFrame(toLong(), TimeUnit.SECONDS)

    /**
     * Returns a [TimeFrame] representing the number of minutes given by the integer value
     *
     * ```kotlin
     * 10.minutes // -> TimeFrame(10, TimeUnit.MINUTES)
     * ```
     */
    @JvmStatic
    inline val Int.minutes: TimeFrame
        get() = TimeFrame(toLong(), TimeUnit.MINUTES)

    /**
     * Returns a [TimeFrame] representing the number of hours given by the integer value
     *
     * ```kotlin
     * 10.hours // -> TimeFrame(10, TimeUnit.HOURS)
     * ```
     */
    @JvmStatic
    inline val Int.hours: TimeFrame
        get() = TimeFrame(toLong(), TimeUnit.HOURS)

    /**
     * Returns a [TimeFrame] representing the number of days given by the integer value
     *
     * ```kotlin
     * 10.days // -> TimeFrame(10, TimeUnit.DAYS)
     * ```
     */
    @JvmStatic
    inline val Int.days: TimeFrame
        get() = TimeFrame(toLong(), TimeUnit.DAYS)

    /**
     * Converts the current [TimeFrame] to the give [unit]
     */
    @JvmStatic
    fun TimeFrame.asUnit(unit: TimeUnit): Long {
        return unit.convert(this.number, this.unit)
    }

    /**
     * Converts the current [TimeFrame] to milliseconds
     */
    @JvmStatic
    fun TimeFrame.inMilliseconds() : Long {
        return asUnit(TimeUnit.MILLISECONDS)
    }

    /**
     * Converts the current [TimeFrame] to seconds
     */
    @JvmStatic
    fun TimeFrame.inSeconds() : Long {
        return asUnit(TimeUnit.SECONDS)
    }

    /**
     * Converts the current [TimeFrame] to minutes
     */
    @JvmStatic
    fun TimeFrame.inMinutes() : Long {
        return asUnit(TimeUnit.MINUTES)
    }

    /**
     * Converts the current [TimeFrame] to hours
     */
    @JvmStatic
    fun TimeFrame.inHours() : Long {
        return asUnit(TimeUnit.HOURS)
    }

    /**
     * Converts the current [TimeFrame] to days
     */
    @JvmStatic
    fun TimeFrame.inDays() : Long {
        return asUnit(TimeUnit.DAYS)
    }
}