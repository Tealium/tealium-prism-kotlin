package com.tealium.prism.core.api.settings.json

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.DataObjectConvertible
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.settings.json.TransformationOperation.Companion.KEY_DESTINATION
import com.tealium.prism.core.api.settings.json.TransformationOperation.Companion.KEY_PARAMETERS

/**
 * An object representing an operation to be performed during a transformation.
 *
 * @param destination The output location where the result of this transformation will be stored
 * @param parameters The parameters necessary for this operation to be performed.
 */
data class TransformationOperation<T : DataItemConvertible>(
    val destination: ReferenceContainer,
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

    /**
     * [DataItemConverter] implementation that handles the conversion of the data found at [KEY_DESTINATION]
     * but delegates the conversion of the data found at [KEY_PARAMETERS] to the given [parameterConverter]
     */
    class Converter<T : DataItemConvertible>(
        private val parameterConverter: DataItemConverter<T>
    ) : DataItemConverter<TransformationOperation<T>> {
        override fun convert(dataItem: DataItem): TransformationOperation<T>? {
            val dataObject = dataItem.getDataObject()
                ?: return null

            val output = dataObject.get(KEY_DESTINATION, ReferenceContainer.Converter)
                    ?: return null

            val parameters = dataObject.get(KEY_PARAMETERS, parameterConverter)
                ?: return null

            return TransformationOperation(output, parameters)
        }
    }
}