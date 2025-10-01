package com.tealium.prism.core.api.tracking

/**
 * The [TrackResult] identifies whether or not a [Dispatch] has been accepted for further processing.
 *
 * @param dispatch The [Dispatch] instance that this [TrackResult] relates to.
 * @param status The status that can be accepted for processing or dropped.
 * @param info Some human readable info regarding the reason behind the status decision.
 */
data class TrackResult(
    val dispatch: Dispatch,
    val status: Status,
    val info: String
) {

    /**
     * Informs if the [Dispatch] has been accepted for processing, or dropped.
     */
    enum class Status {
        Accepted, Dropped
    }

    val description: String
        get() {
            val statusStr = when (status) {
                Status.Dropped -> "dropped"
                Status.Accepted -> "accepted for processing"
            }

            return "Dispatch \"${dispatch.logDescription()}\" has been $statusStr. $info"
        }

    companion object {
        @JvmStatic
        fun accepted(dispatch: Dispatch, info: String) =
            TrackResult(dispatch, Status.Accepted, info)

        @JvmStatic
        fun dropped(dispatch: Dispatch, reason: String) =
            TrackResult(dispatch, Status.Dropped, "Reason: $reason")
    }
}