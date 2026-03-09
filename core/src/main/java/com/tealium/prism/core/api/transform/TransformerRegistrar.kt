package com.tealium.prism.core.api.transform

/**
 * The [TransformerRegistrar] is responsible for registering and unregistering additional
 * [TransformationSettings]s outside of those provided by the main SDK settings.
 */
interface TransformerRegistrar {

    /**
     * Registers an additional [TransformationSettings]
     *
     * @param transformation The [TransformationSettings] to add to the current set of transformations
     */
    fun registerTransformation(transformation: TransformationSettings)

    /**
     * Unregisters the given [transformation] if it is currently registered
     *
     * @param transformation The [TransformationSettings] to remove from the current set of transformations
     */
    fun unregisterTransformation(transformation: TransformationSettings)
}
