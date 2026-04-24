package com.tealium.prism.extensions.internal.lowercase

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.api.transform.Transformer
import com.tealium.prism.extensions.BuildConfig
import com.tealium.prism.extensions.internal.LOWERCASE

class LowercaseTransformer() : Transformer {

    constructor(context: TealiumContext, configuration: DataObject) : this()

    private val converter = LowercaseConfiguration.Converter

    override val id: String = LOWERCASE
    override val version: String = BuildConfig.TEALIUM_LIBRARY_VERSION

    override fun applyTransformation(
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        val config = converter.convert(transformation.configuration.asDataItem())
        if (config == null) {
            completion.invoke(dispatch)
            return
        }

        var payload = dispatch.payload()

        when (val policy = config.policy) {
            is LowercasePolicy.AllVariables -> {
                // Lowercase all string values in the payload
                payload = lowercaseAllStrings(payload)
            }

            is LowercasePolicy.Variables -> {
                // Only lowercase specific variables
                for (input in policy.references) {
                    val item = payload.extract(input.path)
                        ?: continue

                    val lowercased = lowercaseDataItem(item)
                    payload = payload.buildPath(input.path, lowercased)
                }
            }
        }

        dispatch.replace(payload)
        completion.invoke(dispatch)
    }

    private fun lowercaseAllStrings(dataObject: DataObject): DataObject {
        return dataObject.copy {
            dataObject.forEach { (key, item) ->
                if (key != Dispatch.Keys.TEALIUM_VISITOR_ID &&
                    key != Dispatch.Keys.TEALIUM_TRACE_ID &&
                    key != Dispatch.Keys.CP_TRACE_ID
                ) {
                    put(key, lowercaseDataItem(item))
                }
            }
        }
    }

    private fun lowercaseDataItem(item: DataItem): DataItem {
        item.getString()?.let { string ->
            return string.lowercase().asDataItem()
        }

        item.getDataList()?.let { list ->
            return list.map(::lowercaseDataItem)
                .asDataList().asDataItem()
        }

        item.getDataObject()?.let { obj ->
            return obj.copy {
                obj.forEach { (key, value) ->
                    put(key, lowercaseDataItem(value))
                }
            }.asDataItem()
        }

        return item
    }
}
