package com.tealium.core.api.network

import kotlinx.coroutines.flow.Flow

/**
 * The [DelayPolicy] is used to determine whether or not to delay prior to making a network request
 * according to any business rules - primarily used for delaying due to loss of connectivity.
 *
 * @see DoNotDelay
 * @see AfterDelay
 * @see AfterEvent
 */
sealed class DelayPolicy {

    /**
     * This method is used to determine whether or not a delay should happen.
     *
     * @return true if the request needs to be delayed; else false.
     */
    abstract fun shouldDelay() : Boolean
}

/**
 * [DoNotDelay] signifies that there is no need to delay the request before sending.
 */
object DoNotDelay : DelayPolicy() {
    override fun shouldDelay(): Boolean {
        return false
    }
}

/**
 * [AfterDelay] signifies that there should be a fixed delay before the request can be sent.
 * The [interval] should be provided in milliseconds
 *
 * @param interval The length of delay in milliseconds
 */
class AfterDelay(val interval: Long) : DelayPolicy() {

    override fun shouldDelay(): Boolean {
        return true
    }
}

/**
 * [AfterEvent] signifies that there is an event that can be used to trigger the continuation of
 * the network request.
 * The next emission of the [event] [Flow] will trigger the continuation of the network request.
 *
 * @param event The [Flow] to subscribe to indicate safe continuation.
 */
class AfterEvent<T>(val event: Flow<T>) : DelayPolicy() {
    override fun shouldDelay(): Boolean {
        return true
    }
}
