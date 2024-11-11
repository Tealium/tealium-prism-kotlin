package com.tealium.core.api.network

import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.misc.TimeFrameUtils.inSeconds
import com.tealium.core.internal.persistence.database.getTimestamp
import kotlin.math.min

/**
 * A helper class to keep track of a cooldown period based on errors occurring.
 *
 * @param maxInterval The maximum amount of time a cooldown period can last.
 * @param errorBaseInterval The amount of time to extend the cooldown for each consecutive failure, limited
 * by the [maxInterval]
 * @param status Defaults to [CooldownStatus.Success], but this is typically only used for testing.
 * @param timingProvider Provider function to provide the current time
 */
class CooldownHelper internal constructor(
    var maxInterval: TimeFrame,
    val errorBaseInterval: TimeFrame,
    status: CooldownStatus = CooldownStatus.Success,
    private val timingProvider: () -> Long = ::getTimestamp,
) {

    /**
     * The [CooldownStatus] describes the result of a previous event.
     *
     * [CooldownStatus.Success] for events that were successful.
     *
     * [CooldownStatus.Failure] for events that were not successful.
     */
    enum class CooldownStatus {
        Success, Failure
    }

    var status: CooldownStatus = status
        private set
    var failureCount: Int = 0
        private set

    private val cooldownInterval: Long
        get() = min(maxInterval.inSeconds(), errorBaseInterval.inSeconds() * failureCount)

    /**
     * Determines whether or not the Cooldown period, if currently in one, has elapsed.
     *
     * @param lastFetch The time in seconds that the last fetch took place.
     */
    fun isInCooldown(lastFetch: Long): Boolean {
        return if (status == CooldownStatus.Success) false else {
            lastFetch + cooldownInterval >= timingProvider.invoke()
        }
    }

    fun updateStatus(status: CooldownStatus) {
        if (status == CooldownStatus.Success) {
            failureCount = 0
        } else if (status == CooldownStatus.Failure) {
            failureCount++
        }

        this.status = status
    }

    companion object {

        /**
         * Creates a new [CooldownHelper] from the given intervals
         *
         * @param maxInterval The maximum amount of time for a cooldown to last.
         * @param errorBaseInterval The base interval to extend the cooldown period by on each consecutive failure.
         * Note - not providing this parameter will always return `null` from this method.
         */
        @JvmStatic
        fun create(
            maxInterval: TimeFrame,
            errorBaseInterval: TimeFrame?
        ): CooldownHelper? {
            if (errorBaseInterval == null) return null

            return CooldownHelper(maxInterval, errorBaseInterval)
        }
    }
}