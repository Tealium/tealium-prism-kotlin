package com.tealium.core.api

/**
 * Defines the positions during event processing that can be extended.
 *
 * @see Transformer
 */
sealed class DispatchScope() {

    /**
     * This scope happens after data has been collected by any [Collector] implementations in
     * the system; it is also prior to being stored on disk.
     */
    object AfterCollectors : DispatchScope()

    /**
     * This scope happens when the [Dispatch] is being sent to any given [Dispatcher].
     */
    data class Dispatcher(val dispatcher: String) : DispatchScope()
}

/**
 * A [Transformer] has the ability to mutate a [Dispatch] at certain points of the [Dispatch]
 * lifecycle as defined by [DispatchScope].
 */
interface Transformer {

    /**
     * The unique identifier for this [Transformer].
     */
    val id: String

    /**
     * Transforms the given [Dispatch] using the transformation identified by [transformationId]
     *
     * @param transformationId The id of the transformation to apply
     * @param dispatch The [Dispatch] that is being transformed.
     * @param scope The [DispatchScope] that identifies when this transformation is happening.
     *
     * @return The mutated, or non-mutated [dispatch] if it should proceed through the system.
     * Return null to delete this [dispatch] and stop any further processing.
     */
    suspend fun applyTransformation(
        transformationId: String,
        dispatch: Dispatch,
        scope: DispatchScope
    ): Dispatch?
}