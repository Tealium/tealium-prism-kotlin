package com.tealium.core.internal.modules.consent

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataObject

/**
 * Holds all relevant configuration options for the Consent Management system.
 */
class ConsentConfiguration(
    val dispatcherPurposes: Map<String, List<String>>,
    val refireDispatchers: List<String>
) {

    companion object {
        const val KEY_DISPATCHER_PURPOSES = "dispatcher_to_purposes"
        const val KEY_REFIRE_DISPATCHERS = "should_refire_dispatchers"

        /**
         * Convenience method for extracting [ConsentConfiguration] properties from a [DataObject]
         * representation
         */
        fun fromDataObject(configuration: DataObject) : ConsentConfiguration {
            val purposeMap =
                configuration.getDataObject(KEY_DISPATCHER_PURPOSES)?.mapNotNull { entry ->
                    entry.value.getDataList()?.let { value ->
                        entry.key to value.mapNotNull { entry ->
                            entry.getString()
                        }
                    }
                }?.toMap() ?: emptyMap()
            val refireDispatchers =
                configuration.getDataList(KEY_REFIRE_DISPATCHERS)?.mapNotNull(DataItem::getString)?: emptyList()

            return ConsentConfiguration(purposeMap, refireDispatchers)
        }
    }
}
