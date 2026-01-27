package com.tealium.prism.extensions

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.api.transform.TransformationSettingsBuilder
import com.tealium.prism.extensions.internal.PERSIST_DATA_VALUES
import com.tealium.prism.extensions.internal.ValueSource
import com.tealium.prism.extensions.internal.persistdatavalue.PersistDataValuesConfiguration
import com.tealium.prism.extensions.internal.persistdatavalue.PersistValuesUpdateType

/**
 * Builder class for configuring and creating settings for persisting data values.
 *
 * @param transformationId The unique identifier for the transformation operation.
 * This ID is used to associate the settings with a specific transformation.
 */
class PersistDataValuesSettingsBuilder(transformationId: String) :
    TransformationSettingsBuilder<PersistDataValuesSettingsBuilder>(
        transformationId,
        PERSIST_DATA_VALUES
    ) {

    private var input: ValueSource? = null
    private var destination: ReferenceContainer? = null
    private var expiry: Expiry = Expiry.SESSION
    private var updateType: PersistValuesUpdateType = PersistValuesUpdateType.ALLOW_UPDATE

    fun persistValue(
        input: ReferenceContainer,
        destination: ReferenceContainer
    ) = apply {
        this.input = ValueSource.Reference(input)
        this.destination = destination
    }

    fun persistValue(
        input: String,
        destination: ReferenceContainer
    ): PersistDataValuesSettingsBuilder = apply {
        this.input = ValueSource.Constant(ValueContainer(input))
        this.destination = destination
    }

    fun setExpiry(expiry: Expiry): PersistDataValuesSettingsBuilder = apply {
        this.expiry = expiry
    }

    fun setUpdateType(updateType: PersistValuesUpdateType): PersistDataValuesSettingsBuilder = apply {
        this.updateType = updateType
    }

    override fun onBuildConfiguration(): DataObject {
        val input = this.input ?: return DataObject.EMPTY_OBJECT
        val destination = this.destination ?: return DataObject.EMPTY_OBJECT
        val config = PersistDataValuesConfiguration(
            input,
            expiry,
            updateType
        )
        val operation = TransformationOperation(destination, config)
        return operation.asDataObject()
    }
}