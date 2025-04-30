package com.tealium.core.api.settings.json

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataItemUtils.asDataItem
import com.tealium.core.api.data.DataItemUtils.asDataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataObjectConvertible


// TODO - this entire file is a stub, and currently stubbed only to support adding the Mappings to the TransformerCoordinator

object Transformers {
    // TODO - put this somewhere else.
    const val JSON_TRANSFORMER = "json-transformer"
}

enum class JsonOperationType : DataItemConvertible {
    Map;
    // TODO - add the other supported operations

    override fun asDataItem(): DataItem =
        name.lowercase().asDataItem()

    object Converter: DataItemConverter<JsonOperationType> {
        override fun convert(dataItem: DataItem): JsonOperationType? {
            val operationType = dataItem.getString()
                ?: return null

            return values()
                .firstOrNull { it.name.lowercase() == operationType.lowercase() }
        }
    }
}

data class JsonTransformationConfiguration<T : DataItemConvertible>(
    val operationType: JsonOperationType,
    val operations: List<TransformationOperation<T>>
) : DataObjectConvertible {
    override fun asDataObject(): DataObject =
        DataObject.create {
            put(KEY_OPERATION_TYPE, operationType)
            put(KEY_OPERATIONS, operations.asDataList())
        }

    companion object {
        const val KEY_OPERATION_TYPE = "operations_type"
        const val KEY_OPERATIONS = "operations"
    }

    class Converter<T : DataItemConvertible>(private val parameterConverter: DataItemConverter<T>) :
        DataItemConverter<JsonTransformationConfiguration<T>> {
        override fun convert(dataItem: DataItem): JsonTransformationConfiguration<T>? {
            val dataObject = dataItem.getDataObject()
                ?: return null

            val operationType = dataObject.get(KEY_OPERATION_TYPE, JsonOperationType.Converter)
                ?: return null

            val transformationsConverter = TransformationOperation.Converter(parameterConverter)
            val operations = dataObject.getDataList(KEY_OPERATIONS)
                ?.mapNotNull(transformationsConverter::convert)
                ?: return null

            return JsonTransformationConfiguration(operationType, operations)
        }
    }
}