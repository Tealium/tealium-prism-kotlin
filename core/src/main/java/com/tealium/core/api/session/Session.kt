package com.tealium.core.api.session

/**
 * Model of data required to keep track of the current session.
 *
 * @param status The status of this session
 * @param sessionId Unique session id, typically the time in seconds at the time the session started
 * @param lastEventTimeMilliseconds The time in milliseconds of the latest event of this session.
 * @param eventCount The number of events that have occurred in this session
 */
data class Session(
    val status: Status,
    val sessionId: Long,
    val lastEventTimeMilliseconds: Long,
    val eventCount: Int = 1
) {
    /**
     * Models the current status of the session.
     */
    enum class Status {
        /**
         * Indicates that a new session has been started.
         */
        Started,

        /**
         * Indicates that an existing session was resumed.
         */
        Resumed,

        /**
         * Indicates that an existing session was ended.
         */
        Ended
    }
}