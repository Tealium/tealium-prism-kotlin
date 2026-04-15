package com.tealium.prism.extensions.internal.persistdatavalue

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.logIfErrorEnabled
import com.tealium.prism.core.api.data.ValueSource
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.api.transform.Transformer
import com.tealium.prism.extensions.BuildConfig
import com.tealium.prism.extensions.api.persistdatavalue.UpdatePolicy
import com.tealium.prism.extensions.internal.PERSIST_DATA_VALUE

class PersistDataValueTransformer(private val context: TealiumContext) : Transformer {
    constructor(context: TealiumContext, configuration: DataObject) : this(context)

    private val logger = context.logger

    override val id: String = PERSIST_DATA_VALUE
    override val version: String = BuildConfig.TEALIUM_LIBRARY_VERSION

    override fun applyTransformation(
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        val config = PersistDataValueConfiguration.Converter.convert(transformation.configuration.asDataItem())

        if (config == null) {
            completion.invoke(dispatch)
            return
        }

        val dataLayer = context.dataLayer

        val payload = dispatch.payload()

        val destination = config.destination.path
        val input = config.input
        val expiryPolicy = config.expiryPolicy
        val updatePolicy = config.updatePolicy

        if (updatePolicy == UpdatePolicy.KEEP_FIRST_VALUE && dataLayer.extract(destination) != null) {
            // do not overwrite existing value
            completion.invoke(dispatch)
            return
        }

        val dataItem = when (input) {
            is ValueSource.Constant -> {
                input.value.value
            }

            is ValueSource.Reference -> {
                payload.extract(input.reference.path)
            }
        }

        if (dataItem == null) {
            completion.invoke(dispatch)
            return
        }

        try {
            dataLayer.buildPath(destination, dataItem, expiryPolicy.resolve())
            val updatedPayload = payload.buildPath(destination, dataItem)
            dispatch.replace(updatedPayload)

            completion.invoke(dispatch)
        } catch (e: Exception) {
            logger.logIfErrorEnabled(PERSIST_DATA_VALUE) {
                "Failed to persist data value for path $destination"
            }
            completion.invoke(dispatch)
        }
    }
}
