package com.tealium.core.api.misc

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

inline val Int.nanoseconds : TimeFrame
    get() = TimeFrame(toLong(), TimeUnit.NANOSECONDS)

inline val Int.microseconds : TimeFrame
    get() = TimeFrame(toLong(), TimeUnit.MICROSECONDS)

inline val Int.milliseconds : TimeFrame
    get() = TimeFrame(toLong(), TimeUnit.MILLISECONDS)

inline val Int.seconds : TimeFrame
    get() = TimeFrame(toLong(), TimeUnit.SECONDS)

inline val Int.minutes : TimeFrame
    get() = TimeFrame(toLong(), TimeUnit.MINUTES)

inline val Int.hours : TimeFrame
    get() = TimeFrame(toLong(), TimeUnit.HOURS)

inline val Int.days : TimeFrame
    get() = TimeFrame(toLong(), TimeUnit.DAYS)