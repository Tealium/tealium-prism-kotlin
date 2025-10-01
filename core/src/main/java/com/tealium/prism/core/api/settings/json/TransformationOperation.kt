package com.tealium.prism.core.api.settings.json

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.DataObjectConvertible
import com.tealium.prism.core.api.settings.VariableAccessor

data class TransformationOperation<T : DataItemConvertible>(
    val destination: VariableAccessor,
    val parameters: T
) : DataObjectConvertible {
    override fun asDataObject(): DataObject =
        DataObject.create {
            put(KEY_DESTINATION, destination)
            put(KEY_PARAMETERS, parameters)
        }

    companion object {
        const val KEY_DESTINATION = "destination"
        const val KEY_PARAMETERS = "parameters"
    }

    class Converter<T : DataItemConvertible>(
        private val parameterConverter: DataItemConverter<T>
    ) : DataItemConverter<TransformationOperation<T>> {
        override fun convert(dataItem: DataItem): TransformationOperation<T>? {
            val dataObject = dataItem.getDataObject()
                ?: return null

            val output = dataObject.get(KEY_DESTINATION, VariableAccessor.Converter)
                ?: return null

            val parameters = dataObject.get(KEY_PARAMETERS, parameterConverter)
                ?: return null

            return TransformationOperation(output, parameters)
        }
    }
}