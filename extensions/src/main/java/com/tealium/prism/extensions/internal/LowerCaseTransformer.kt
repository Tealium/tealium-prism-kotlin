package com.tealium.prism.extensions.internal

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.api.transform.Transformer
import com.tealium.prism.extensions.BuildConfig

class LowerCaseTransformer() : Transformer {

    constructor(context: TealiumContext, configuration: DataObject) : this()

    private val converter = TransformationOperation.Converter(LowerCaseInput.Converter)

    override val id: String = LOWERCASE
    override val version: String = BuildConfig.TEALIUM_LIBRARY_VERSION

    override fun applyTransformation(
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        val allVariables = transformation.configuration.getBoolean(LowerCaseKeys.ALL_VARIABLES) ?: false
        val operations = transformation.configuration.getDataList(LowerCaseKeys.OPERATIONS)
            ?.mapNotNull(converter::convert)

        var payload = dispatch.payload()

        if (allVariables) {
            // Lowercase all string values in the payload
            payload = lowercaseAllStrings(payload)
        } else if (operations != null) {
            // Only lowercase specific operations
            for (operation in operations) {
                val item = payload.extract(operation.parameters.input.path)
                    ?: continue
                
                val stringValue = item.getString()
                if (stringValue != null) {
                    val lowercased = stringValue.lowercase()
                    payload = payload.buildPath(operation.destination.path, lowercased.asDataItem())
                }
            }
        }

        dispatch.replace(payload)
        completion.invoke(dispatch)
    }

    private fun lowercaseAllStrings(dataObject: DataObject): DataObject {
        return dataObject.copy {
            dataObject.forEach { (key, item) ->
                put(key, lowercaseDataItem(item))
            }
        }
    }

    private fun lowercaseDataItem(item: DataItem): DataItem {
        return when {
            item.isString() -> {
                item.getString()?.lowercase()?.asDataItem() ?: item
            }
            item.isDataList() -> {
                val list = item.getDataList() ?: return item
                list.map { lowercaseDataItem(it) }.asDataList().asDataItem()
            }
            item.isDataObject() -> {
                val obj = item.getDataObject() ?: return item
                lowercaseAllStrings(obj).asDataItem()
            }
            else -> item
        }
    }
}
