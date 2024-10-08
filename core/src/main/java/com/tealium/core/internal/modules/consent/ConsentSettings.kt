package com.tealium.core.internal.modules.consent

import com.tealium.core.api.data.DataObject

/**
 * Holds all relevant configuration options for the Consent Management system.
 */
class ConsentSettings(
    val dispatcherPurposes: Map<String, List<String>>,
    val refireDispatchers: List<String>
) {

    companion object {
        const val KEY_DISPATCHER_PURPOSES = "dispatcher_to_purposes"
        const val KEY_REFIRE_DISPATCHERS = "should_refire_dispatchers"

        /**
         * Convenience method for
         */
        fun fromDataObject(dataObject: DataObject) : ConsentSettings {
            val purposeMap =
                dataObject.getDataObject(KEY_DISPATCHER_PURPOSES)?.getAll()?.mapNotNull { entry ->
                    entry.value.getDataList()?.let { value ->
                        entry.key to value.mapNotNull { entry ->
                            entry.getString()
                        }
                    }
                }?.toMap() ?: emptyMap()
            val refireDispatchers =
                dataObject.getDataList(KEY_REFIRE_DISPATCHERS)?.mapNotNull { it.getString() } ?: emptyList()

            return ConsentSettings(purposeMap, refireDispatchers)
        }
    }
}
