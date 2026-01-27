package com.tealium.prism.extensions

import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.api.transform.TransformationSettingsBuilder
import com.tealium.prism.extensions.internal.SET_DATA_VALUES
import com.tealium.prism.extensions.internal.SetDataValuesInput
import com.tealium.prism.extensions.internal.ValueSource

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