package com.tealium.prism.extensions.internal

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.api.transform.Transformer
import com.tealium.prism.extensions.BuildConfig

// TODO - move to public usage
sealed class ValueSource {
    data class Reference(val reference: ReferenceContainer) : ValueSource()
    data class Constant(val value: ValueContainer) : ValueSource()
}

class SetDataValuesInput(
    val input: ValueSource
): DataItemConvertible {
    override fun asDataItem(): DataItem {
        return when (input) {
            is ValueSource.Reference -> input.reference.asDataItem()
            is ValueSource.Constant -> input.value.asDataItem()
        }
    }

    object Converter : DataItemConverter<SetDataValuesInput> {
        override fun convert(dataItem: DataItem): SetDataValuesInput? {
            val referenceContainer = ReferenceContainer.Converter.convert(dataItem)
            if (referenceContainer != null)
                return SetDataValuesInput(ValueSource.Reference(referenceContainer))

            val valueContainer = ValueContainer.Converter.convert(dataItem)
            if (valueContainer != null)
                return SetDataValuesInput(ValueSource.Constant(valueContainer))

            return null
        }
    }
}

class SetDataValuesTransformer() : Transformer {

    constructor(context: TealiumContext, configuration: DataObject): this()

    private val converter = TransformationOperation.Converter(SetDataValuesInput.Converter)

    override val id: String = SET_DATA_VALUES
    override val version: String = BuildConfig.TEALIUM_LIBRARY_VERSION

    override fun applyTransformation(
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        val operations = transformation.configuration.getDataList("operations")
            ?.mapNotNull(converter::convert)

        if (operations.isNullOrEmpty()) {
            completion.invoke(dispatch)
            return
        }

        var payload = dispatch.payload()
        for (operation in operations) {
            val input = operation.parameters.input
            when (input) {
                is ValueSource.Reference -> {
                    val item = payload.extract(input.reference.path)
                        ?: continue
                    payload = payload.buildPath(operation.destination.path, item)
                }
                is ValueSource.Constant -> {
                    // TODO - this only works for strings, as it's restricted by ValueContainer
                    payload = payload.buildPath(operation.destination.path, input.value.value.asDataItem())
                }
            }
        }

        dispatch.replace(payload)
        completion.invoke(dispatch)
    }
}
