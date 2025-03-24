package com.tealium.core.api.settings

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataList
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.internal.modules.consent.ConsentSettings

/**
 * A builder that allows setting the configurable properties of the Consent module.
 */
class ConsentSettingsBuilder : ModuleSettingsBuilder() {

    /**
     * Sets the required purposes for each [Dispatcher]
     */
    fun setDispatcherToPurposes(dispatcherToPurposes: Map<String, Set<String>>) = apply {
        configuration.put(ConsentSettings.KEY_DISPATCHER_PURPOSES, DataObject.fromMap(dispatcherToPurposes))
    }

    /**
     * Sets the list of [Dispatcher]s that support events being refired with updated consent purposes.
     */
    fun setShouldRefireDispatchers(refireDispatchers: Set<String>) = apply {
        configuration.put(
            ConsentSettings.KEY_REFIRE_DISPATCHERS,
            DataList.fromCollection(refireDispatchers).asDataItem()
        )
    }
}