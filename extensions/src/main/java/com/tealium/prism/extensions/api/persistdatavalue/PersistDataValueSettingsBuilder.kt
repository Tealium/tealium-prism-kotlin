package com.tealium.prism.extensions.api.persistdatavalue

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.data.ValueSource
import com.tealium.prism.core.api.misc.ExpiryPolicy
import com.tealium.prism.core.api.transform.TransformationSettingsBuilder
import com.tealium.prism.extensions.internal.PERSIST_DATA_VALUE
import com.tealium.prism.extensions.internal.persistdatavalue.PersistDataValueConfiguration

/**
 * Builder class for configuring and creating settings for persisting data values.
 *
 * @param transformationId The unique identifier for the transformation operation.
 * This ID is used to associate the settings with a specific transformation.
 */
class PersistDataValueSettingsBuilder(transformationId: String) :
    TransformationSettingsBuilder<PersistDataValueSettingsBuilder>(
        transformationId,
        PERSIST_DATA_VALUE
    ) {

    private var input: ValueSource? = null
    private var destination: ReferenceContainer? = null
    private var expiryPolicy: ExpiryPolicy? = null
    private var updatePolicy: UpdatePolicy? = null

    /**
     * Configures the settings to persist a value from a reference container to a given destination.
     *
     * @param input The reference container that holds the value to be persisted.
     * @param destination The reference container where the value should be persisted to.
     */
    fun persistFrom(
        input: ReferenceContainer,
        destination: ReferenceContainer
    ) = apply {
        this.input = ValueSource.Reference(input)
        this.destination = destination
    }

    /**
     * Configures the settings to persist a constant value to a given destination.
     *
     * @param input The constant value to be persisted.
     * @param destination The reference container where the value should be persisted to.
     */
    fun persistConstant(
        input: DataItem,
        destination: ReferenceContainer
    ): PersistDataValueSettingsBuilder = apply {
        this.input = ValueSource.Constant(ValueContainer(input.asDataItem()))
        this.destination = destination
    }

    /**
     * Sets the expiry policy for the persisted value. The expiry policy determines how long the
     * persisted value should be retained before it expires. By default, the expiry policy is set
     * to SESSION, which means the value will expire at the end of the session.
     *
     * @param expiryPolicy The expiry policy to be applied to the persisted value.
     */
    fun setExpiryPolicy(expiryPolicy: ExpiryPolicy): PersistDataValueSettingsBuilder = apply {
        this.expiryPolicy = expiryPolicy
    }

    /**
     * Sets the update policy for the persisted value. The update policy determines whether the
     * persisted value can be updated with new values after it has been set. By default, the update
     * policy is set to ALLOW_UPDATE, which means the value can be updated with new values.
     *
     * @param updatePolicy The update policy to be applied to the persisted value.
     */
    fun setUpdatePolicy(updatePolicy: UpdatePolicy): PersistDataValueSettingsBuilder = apply {
        this.updatePolicy = updatePolicy
    }

    override fun onBuildConfiguration(): DataObject {
        val settings = DataObject.create {
            input?.let {
                val valueSource = when (it) {
                    is ValueSource.Reference -> it.reference
                    is ValueSource.Constant -> it.value
                }
                put(PersistDataValueConfiguration.Converter.KEY_INPUT, valueSource)
            }

            expiryPolicy?.let {
                put(PersistDataValueConfiguration.Converter.KEY_DURATION, it)
            }

            updatePolicy?.let {
                put(PersistDataValueConfiguration.Converter.KEY_UPDATE_POLICY, it)
            }

            destination?.let { put(PersistDataValueConfiguration.Converter.KEY_DESTINATION, it) }
        }
        return settings
    }
}