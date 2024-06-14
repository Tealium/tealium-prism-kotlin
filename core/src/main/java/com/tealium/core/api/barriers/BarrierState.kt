package com.tealium.core.api.barriers

/**
 * The defined states that a [Barrier] implementation can have at any given time.
 */
enum class BarrierState {
    /**
     * Indicates that a [Barrier] is currently closed.
     */
    Closed,

    /**
     * Indicates that a [Barrier] is currently open.
     */
    Open
}