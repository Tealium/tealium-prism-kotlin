package com.tealium.core.internal.persistence

import java.util.concurrent.TimeUnit

/**
 * Class denoting a length of time that is easily convertible into any required time units
 */
data class TimeFrame(
    val number: Long,
    val unit: TimeUnit
)