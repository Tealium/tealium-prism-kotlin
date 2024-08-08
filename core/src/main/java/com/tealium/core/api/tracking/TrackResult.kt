package com.tealium.core.api.tracking

/**
 * The [TrackResult] identifies whether or not a [Dispatch] has been accepted for further processing.
 */
sealed class TrackResult {

    /**
     * [Dropped] will indicate that the [Dispatch] was stopped from further processing - e.g. by a
     * [Transformer] or consent decision - and as such, it will not be stored or sent for further
     * processing.
     */
    object Dropped: TrackResult()

    /**
     * [Accepted] will indicate that the [Dispatch] has been accepted by the SDK, and it therefore
     * persisted for future processing - e.g. by [Dispatcher]s or the consent process.
     */
    object Accepted: TrackResult()
}