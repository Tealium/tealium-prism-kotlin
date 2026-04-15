package com.tealium.prism.core.api.misc

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.misc.TimeFrameUtils.inSeconds
import com.tealium.prism.core.api.persistence.Expiry
import java.util.concurrent.TimeUnit

/**
 * Represents the Expiry Policy for a persisted data value. Unlike the Expiry class, which may hold
 * a specific expiry time, the ExpiryPolicy produces an Expiry based on the current time when
 * resolved.
 */
sealed class ExpiryPolicy(val value: Long): DataItemConvertible {
    private data object Session : ExpiryPolicy(-2)
    private data object Forever : ExpiryPolicy(-1)
    private data object UntilRestart : ExpiryPolicy(-3)
    private class Duration(val timeFrame: TimeFrame) : ExpiryPolicy(timeFrame.inSeconds())

    override fun asDataItem(): DataItem {
        return DataItem.long(value)
    }

    /**
     * Resolves the ExpiryPolicy to an Expiry based on the current time.
     */
    fun resolve(): Expiry {
        return when (this) {
            is Session -> Expiry.SESSION
            is UntilRestart -> Expiry.UNTIL_RESTART
            is Forever -> Expiry.FOREVER
            is Duration -> Expiry.afterTimeUnit(timeFrame.number, timeFrame.unit)
        }
    }

    object Converter : DataItemConverter<ExpiryPolicy> {
        override fun convert(dataItem: DataItem): ExpiryPolicy? {
            val value = dataItem.getLong() ?: return null
            return when (value) {
                Session.value -> Session
                Forever.value -> Forever
                UntilRestart.value -> UntilRestart
                else -> if (value >= 0) Duration(TimeFrame(value, TimeUnit.SECONDS)) else return null
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if(javaClass != other?.javaClass) return false

        other as ExpiryPolicy
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    companion object {

        /**
         * [ExpiryPolicy] tied to the duration of the current session.
         */
        @JvmField
        val SESSION: ExpiryPolicy = Session

        /**
         * [ExpiryPolicy] that will never expire.
         */
        @JvmField
        val FOREVER: ExpiryPolicy = Forever

        /**
         * [ExpiryPolicy] that will expire upon the next launch of the application.
         */
        @JvmField
        val UNTIL_RESTART: ExpiryPolicy = UntilRestart

        /**
         * [ExpiryPolicy] that will expire after the specified duration has passed.
         * The duration is based on the time unit and value of the provided TimeFrame.
         */
        @JvmStatic
        fun duration(timeFrame: TimeFrame): ExpiryPolicy {
            return Duration(timeFrame)
        }
    }
}