package com.tealium.prism.core.api.transform

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.Rule

/**
 * A builder class for configuring the settings available for Transformations. This base class
 * allows the configuration of settings that are common to all Transformations.
 *
 * Subclasses can add additional methods to provide custom settings that are required by those
 * Transformations. They should do this by overriding [onBuildConfiguration] and returning those
 * custom settings. These will be surfaced on the [TransformationSettings.configuration] object.
 */
abstract class TransformationSettingsBuilder<T : TransformationSettingsBuilder<T>>(
    val transformationId: String,
    val transformerId: String
) {
    private var scope: TransformationScope? = null
    private var condition: Rule<Condition>? = null
    private var order: Int? = null

    /**
     * Sets the [TransformationScope] that the [transformationId] is applicable for.
     *
     * @return this [TransformationSettingsBuilder] to continue configuration
     */
    @Suppress("UNCHECKED_CAST")
    fun setScope(scope: TransformationScope) = apply {
        this.scope = scope
    } as T

    /**
     * Sets the optional conditions that must be matched in order for the [transformationId] to be
     * executed.
     *
     * @return this [TransformationSettingsBuilder] to continue configuration
     */
    @Suppress("UNCHECKED_CAST")
    fun setCondition(condition: Rule<Condition>) = apply {
        this.condition = condition
    } as T

    /**
     * Sets when this [transformationId] should be executed. Transformations are executed in ascending
     * order, that is, a Transformation with order 1 will be executed before a Transformation with
     * order 2.
     *
     * Duplicate values for order _should_ be executed in insertion order according to the settings,
     * but should not be relied upon.
     *
     * @return this [TransformationSettingsBuilder] to continue configuration
     */
    @Suppress("UNCHECKED_CAST")
    fun setOrder(order: Int) = apply {
        this.order = order
    } as T

    protected open fun onBuildConfiguration(): DataObject =
        DataObject.EMPTY_OBJECT

    fun build(): DataObject {
        val config = onBuildConfiguration()
        return DataObject.create {
            put(TransformationSettings.Converter.KEY_TRANSFORMATION_ID, transformationId)
            put(TransformationSettings.Converter.KEY_TRANSFORMER_ID, transformerId)
            put(TransformationSettings.Converter.KEY_CONFIGURATION, config)

            scope?.let {
                put(TransformationSettings.Converter.KEY_SCOPE, it)
            }
            order?.let {
                put(TransformationSettings.Converter.KEY_ORDER, it)
            }
            condition?.let {
                put(TransformationSettings.Converter.KEY_CONDITIONS, it)
            }
        }
    }
}