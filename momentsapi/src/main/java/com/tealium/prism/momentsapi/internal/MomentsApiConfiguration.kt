package com.tealium.prism.momentsapi.internal

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.momentsapi.MomentsApiRegion

/**
 * Configuration for the MomentsApi module.
 */
data class MomentsApiConfiguration(
    val region: MomentsApiRegion,
    val referrer: String? = null
) {
    companion object {
        const val KEY_REGION = "moments_api_region"
        const val KEY_REFERRER = "moments_api_referrer"
        
        /**
         * Creates a [MomentsApiConfiguration] from a [DataObject].
         * 
         * @param configuration The DataObject containing the configuration
         * @return The configuration instance, or null if the required region is missing
         */
        fun fromDataObject(configuration: DataObject): MomentsApiConfiguration? {
            val region = configuration.get(KEY_REGION, Converters.MomentsApiRegionConverter)
                ?: return null
            
            val referrer = configuration.getString(KEY_REFERRER)
            
            return MomentsApiConfiguration(
                region = region,
                referrer = referrer
            )
        }
    }
}
