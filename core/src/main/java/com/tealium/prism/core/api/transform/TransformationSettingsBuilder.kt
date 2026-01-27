package com.tealium.prism.core.api.transform

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.Rule

abstract class TransformationSettingsBuilder<T : TransformationSettingsBuilder<T>>(
    private val transformationId: String,
    private val transformerId: String
) {
    private var scopes = mutableSetOf<TransformationScope>()
    private var condition: Rule<Condition>? = null

    @Suppress("UNCHECKED_CAST")
    fun addScope(scope: TransformationScope) = apply {
        scopes.add(scope)
    } as T

    @Suppress("UNCHECKED_CAST")
    fun setCondition(condition: Rule<Condition>) = apply {
        this.condition = condition
    } as T

    protected open fun onBuildConfiguration(): DataObject =
        DataObject.EMPTY_OBJECT

    fun build(): TransformationSettings {
        return TransformationSettings(
            transformationId,
            transformerId,
            scopes,
            onBuildConfiguration(),
            condition
        )
    }
}