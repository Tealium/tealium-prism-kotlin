package com.tealium.prism.core.api.settings

import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.settings.consent.ConsentConfiguration
import com.tealium.prism.core.internal.settings.consent.ConsentPurpose

/**
 * A builder that allows setting the configurable properties of the Consent module.
 */
class ConsentConfigurationBuilder {

    private val builder = DataObject.Builder()
    private val purposes = DataObject.Builder()

    /**
     * Sets the purpose used to give consent to the entire SDK
     */
    fun setTealiumPurposeId(tealiumPurposeId: String) = apply {
        builder.put(ConsentConfiguration.Converter.KEY_TEALIUM_PURPOSE_ID, tealiumPurposeId)
    }

    /**
     * Adds a [purposeId] that is required to be accepted in order for each of the given [dispatcherIds]
     * to process a [Dispatch]
     */
    fun addPurpose(purposeId: String, dispatcherIds: Set<String>) = apply {
        purposes.put(purposeId, ConsentPurpose(purposeId, dispatcherIds))
    }

    /**
     * Sets the list of [Dispatcher]s that support events being refired with updated consent purposes.
     */
    fun setRefireDispatcherIds(refireDispatchers: Set<String>) = apply {
        builder.put(
            ConsentConfiguration.Converter.KEY_REFIRE_DISPATCHER_IDS,
            DataList.fromCollection(refireDispatchers).asDataItem()
        )
    }

    fun build(): DataObject {
        builder.put(ConsentConfiguration.Converter.KEY_PURPOSES, purposes.build())

        return builder.build()
    }
}