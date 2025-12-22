package com.tealium.prism.extensions.internal.persistdatavalue

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.api.transform.Transformer
import com.tealium.prism.core.internal.modules.datalayer.DataLayerModule
import com.tealium.prism.extensions.BuildConfig
import com.tealium.prism.extensions.internal.PERSIST_DATA_VALUES
import com.tealium.prism.extensions.internal.ValueSource

class PersistDataValuesTransformer(private val context: TealiumContext) : Transformer {
    constructor(context: TealiumContext, configuration: DataObject) : this(context)

    private val converter = TransformationOperation.Converter(PersistDataValuesConfiguration.Convert)

    override val id: String = PERSIST_DATA_VALUES
    override val version: String = BuildConfig.TEALIUM_LIBRARY_VERSION

    override fun applyTransformation(
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        // only one at a time, doesn't need to be a list
        val operation = converter.convert(transformation.configuration.asDataItem())

        if (operation == null) {
            completion.invoke(dispatch)
            return
        }

        val datalayer = context.moduleManager
            .getModuleOfType(DataLayerModule::class.java)
            ?.dataStore
        if (datalayer == null) {
            completion.invoke(dispatch)
            return
        }

        var payload = dispatch.payload()

        val input = operation.parameters.input
        val expiry = operation.parameters.expiry
        val updateType = operation.parameters.updateType

        val item = datalayer.extract(operation.destination.path)

        if (updateType == PersistValuesUpdateType.KEEP_FIRST_VALUE && item != null) {
            // do not overwrite existing value
            completion.invoke(dispatch)
            return
        }

        val dataItem = when (input) {
            is ValueSource.Constant -> {
                input.value.value.asDataItem()
            }

            is ValueSource.Reference -> {
                payload.extract(input.reference.path)
            }
        }

        if (dataItem == null) {
            completion.invoke(dispatch)
            return
        }

        payload = payload.buildPath(operation.destination.path, dataItem)
        dispatch.replace(payload)

        datalayer.buildPath(operation.destination.path, dataItem, expiry)

        completion.invoke(dispatch)
    }
}
