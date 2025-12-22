package com.tealium.prism.extensions

import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.extensions.internal.SET_DATA_VALUES
import com.tealium.prism.extensions.internal.SetDataValuesInput
import com.tealium.prism.extensions.internal.ValueSource

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

    abstract fun onBuildConfiguration(): DataObject

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

class SetDataValuesSettingsBuilder(transformationId: String) :
    TransformationSettingsBuilder<SetDataValuesSettingsBuilder>(transformationId, SET_DATA_VALUES) {

    private val operations = mutableListOf<TransformationOperation<SetDataValuesInput>>()

    fun addOperation(
        input: ReferenceContainer,
        destination: ReferenceContainer
    ): SetDataValuesSettingsBuilder = apply {
        operations.add(
            TransformationOperation(destination, SetDataValuesInput(ValueSource.Reference(input)))
        )
    }

    fun addOperation(
        input: String,
        destination: ReferenceContainer
    ): SetDataValuesSettingsBuilder = apply {
        operations.add(
            TransformationOperation(
                destination,
                SetDataValuesInput(ValueSource.Constant(ValueContainer(input)))
            )
        )
    }

    override fun onBuildConfiguration(): DataObject {
        return DataObject.create {
            put("operations", operations.map(DataItemConvertible::asDataItem).asDataList())
        }
    }
}