package com.tealium.prism.extensions.internal.persistdatavalue

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.logIfErrorEnabled
import com.tealium.prism.core.api.data.ValueSource
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
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
        completion: Callback<Dispatch?>
    ): Disposable {
        val config = PersistDataValueConfiguration.Converter.convert(transformation.configuration.asDataItem())

        if (config == null) {
            completion.onComplete(dispatch)
            return Disposables.disposed()
        }

        val dataLayer = context.dataLayer

        val payload = dispatch.payload()

        val destination = config.destination.path
        val input = config.input
        val expiryPolicy = config.expiryPolicy
        val updatePolicy = config.updatePolicy

        val dataItem = when (input) {
            is ValueSource.Constant -> {
                input.value.value
            }

            is ValueSource.Reference -> {
                payload.extract(input.reference.path)
            }
        }

        if (dataItem == null) {
            completion.onComplete(dispatch)
            return Disposables.disposed()
        }

        val keepExistingValue = updatePolicy == UpdatePolicy.KEEP_FIRST_VALUE && try {
            dataLayer.extract(destination) != null
        } catch (e: Exception) {
            // Exception, not PersistenceException: a failed read throws the database's own exception
            // (SQLiteException or IllegalStateException, for example). A narrower catch fits only if
            // reads throw PersistenceException. A value that can't be read is not overwritten.
            logger.logIfErrorEnabled(PERSIST_DATA_VALUE) {
                "Failed to read data value for path $destination; not overwriting it: ${e.message}"
            }
            true
        }

        if (!keepExistingValue) {
            try {
                dataLayer.buildPath(destination, dataItem, expiryPolicy.resolve())
                val updatedPayload = payload.buildPath(destination, dataItem)
                dispatch.replace(updatedPayload)
            } catch (e: Exception) {
                // Exception: for a nested path buildPath first reads the existing value, and a failed read
                // throws the database's own exception; onDataUpdated/onDataRemoved subscribers, app code
                // included, run inside it and can throw anything.
                logger.logIfErrorEnabled(PERSIST_DATA_VALUE) {
                    "Failed to persist data value for path $destination: ${e.message}"
                }
            }
        }
        completion.onComplete(dispatch)
        // Nothing to cancel: this transformer completes synchronously
        return Disposables.disposed()
    }
}
