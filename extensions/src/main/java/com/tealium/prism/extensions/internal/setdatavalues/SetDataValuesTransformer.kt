package com.tealium.prism.extensions.internal.setdatavalues

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ValueSource
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.api.transform.Transformer
import com.tealium.prism.extensions.BuildConfig
import com.tealium.prism.extensions.internal.SET_DATA_VALUES

class SetDataValuesTransformer() : Transformer {
    constructor(context: TealiumContext, configuration: DataObject) : this()

    override val id: String = SET_DATA_VALUES
    override val version: String = BuildConfig.TEALIUM_LIBRARY_VERSION

    override fun applyTransformation(
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        val config = SetDataValuesConfiguration.Converter.convert(transformation.configuration.asDataItem())
        val operations = config?.operations

        if (operations.isNullOrEmpty()) {
            completion.invoke(dispatch)
            return
        }

        var payload = dispatch.payload()
        for (operation in operations) {
            val input = operation.input
            when (input) {
                is ValueSource.Reference -> {
                    val item = payload.extract(input.reference.path)
                        ?: continue
                    payload = payload.buildPath(operation.destination.path, item)
                }

                is ValueSource.Constant -> {
                    payload = payload.buildPath(
                        operation.destination.path,
                        input.value.value
                    )
                }
            }
        }

        dispatch.replace(payload)
        completion.invoke(dispatch)
    }
}
