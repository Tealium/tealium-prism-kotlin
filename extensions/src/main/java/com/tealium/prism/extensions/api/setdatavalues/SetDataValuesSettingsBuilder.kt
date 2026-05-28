package com.tealium.prism.extensions.api.setdatavalues

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.data.ValueSource
import com.tealium.prism.core.api.transform.TransformationSettingsBuilder
import com.tealium.prism.extensions.internal.SET_DATA_VALUES
import com.tealium.prism.extensions.internal.setdatavalues.SetDataValuesConfiguration
import com.tealium.prism.extensions.internal.setdatavalues.SetDataValuesOperation

/**
 * Builder class for configuring and creating settings for setting data values.
 *
 * @param transformationId The unique identifier for the transformation operation.
 * This ID is used to associate the settings with a specific transformation.
 */
class SetDataValuesSettingsBuilder(transformationId: String) :
    TransformationSettingsBuilder<SetDataValuesSettingsBuilder>(transformationId, SET_DATA_VALUES) {

    private val operations = mutableListOf<SetDataValuesOperation>()

    /**
     * Adds an operation to set a data value from a reference input to a destination reference.
     *
     * @param input The reference to extract the value from.
     * @param destination The reference to set the value to in the payload.
     */
    fun setFrom(
        input: ReferenceContainer,
        destination: ReferenceContainer
    ): SetDataValuesSettingsBuilder = apply {
        operations.add(
            SetDataValuesOperation(ValueSource.Reference(input), destination)
        )
    }

    /**
     * Adds an operation to set a data value from a constant input to a destination reference.
     *
     * @param input The constant value to set.
     * @param destination The reference to set the value to in the payload.
     */
    fun setConstant(
        input: DataItem,
        destination: ReferenceContainer
    ): SetDataValuesSettingsBuilder = apply {
        operations.add(
            SetDataValuesOperation(ValueSource.Constant(ValueContainer(input)), destination)
        )
    }

    override fun onBuildConfiguration(): DataObject {
        return DataObject.create {
            if (operations.isNotEmpty()) {
                put(SetDataValuesConfiguration.Converter.KEY_OPERATIONS, operations.asDataList())
            }
        }
    }
}