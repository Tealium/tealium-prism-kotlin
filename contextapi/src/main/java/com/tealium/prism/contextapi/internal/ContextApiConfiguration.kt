package com.tealium.prism.contextapi.internal

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.contextapi.ContextApiRegion

/**
 * Configuration for the ContextApi module.
 */
data class ContextApiConfiguration(
    val region: ContextApiRegion,
    val referrer: String? = null
) {
    companion object {
        const val KEY_REGION = "context_api_region"
        const val KEY_REFERRER = "context_api_referrer"
        
        /**
         * Creates a [ContextApiConfiguration] from a [DataObject].
         * 
         * @param configuration The DataObject containing the configuration
         * @return The configuration instance, or null if the required region is missing
         */
        fun fromDataObject(configuration: DataObject): ContextApiConfiguration? {
            val region = configuration.get(KEY_REGION, Converters.ContextApiRegionConverter)
                ?: return null
            
            val referrer = configuration.getString(KEY_REFERRER)
            
            return ContextApiConfiguration(
                region = region,
                referrer = referrer
            )
        }
    }
}
