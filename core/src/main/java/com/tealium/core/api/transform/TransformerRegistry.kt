package com.tealium.core.api.transform

/**
 * The [TransformerRegistry] is responsible for registering and unregistering additional [Transformer]s
 * and [ScopedTransformation]s outside of those provided by the main SDK settings.
 */
interface TransformerRegistry {

    /**
     * Registers an additional [Transformer]
     *
     * @param transformer The [Transformer] to add to the current set of transformers
     */
    fun registerTransformer(transformer: Transformer)

    /**
     * Unregisters the given [transformer] if it is currently registered
     *
     * @param transformer The [Transformer] to remove from the current set of transformers
     */
    fun unregisterTransformer(transformer: Transformer)

    /**
     * Registers an additional [ScopedTransformation]
     *
     * @param transformation The [ScopedTransformation] to add to the current set of transformations
     */
    fun registerScopedTransformation(transformation: ScopedTransformation)

    /**
     * Unregisters the given [transformation] if it is currently registered
     *
     * @param transformation The [ScopedTransformation] to remove from the current set of transformations
     */
    fun unregisterScopedTransformation(transformation: ScopedTransformation)
}