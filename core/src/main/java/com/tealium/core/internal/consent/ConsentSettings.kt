package com.tealium.core.internal.consent

import com.tealium.core.api.data.TealiumBundle

/**
 * Holds all relevant configuration options for the Consent Management system.
 */
class ConsentSettings(
    val enabled: Boolean,
    val dispatcherPurposes: Map<String, List<String>>,
    val refireDispatchers: List<String>
) {
    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_DISPATCHER_PURPOSES = "dispatcher_to_purposes"
        const val KEY_REFIRE_DISPATCHERS = "should_refire_dispatchers"

        /**
         * Convenience method for
         */
        fun fromBundle(bundle: TealiumBundle) : ConsentSettings {
            val enabled = bundle.getBoolean(KEY_ENABLED) ?: false
            val purposeMap =
                bundle.getBundle(KEY_DISPATCHER_PURPOSES)?.getAll()?.mapNotNull { entry ->
                    entry.value.getList()?.let { value ->
                        entry.key to value.mapNotNull { entry ->
                            entry.getString()
                        }
                    }
                }?.toMap() ?: emptyMap()
            val refireDispatchers =
                bundle.getList(KEY_REFIRE_DISPATCHERS)?.mapNotNull { it.getString() } ?: emptyList()

            return ConsentSettings(enabled, purposeMap, refireDispatchers)
        }
    }
}
