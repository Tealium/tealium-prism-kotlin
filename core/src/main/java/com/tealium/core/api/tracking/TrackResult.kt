package com.tealium.core.api.tracking

/**
 * The [TrackResult] identifies whether or not a [Dispatch] has been accepted for further processing.
 */
sealed class TrackResult {
    /**
     * The [Dispatch] instance that this [TrackResult] relates to.
     */
    abstract val dispatch: Dispatch

    /**
     * [Dropped] will indicate that the [Dispatch] was stopped from further processing - e.g. by a
     * [Transformer] or consent decision - and as such, it will not be stored or sent for further
     * processing.
     */
    data class Dropped(override val dispatch: Dispatch): TrackResult()

    /**
     * [Accepted] will indicate that the [Dispatch] has been accepted by the SDK, and it therefore
     * persisted for future processing - e.g. by [Dispatcher]s or the consent process.
     */
    data class Accepted(override val dispatch: Dispatch): TrackResult()
}