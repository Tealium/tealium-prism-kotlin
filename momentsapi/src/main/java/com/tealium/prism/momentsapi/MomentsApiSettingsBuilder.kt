package com.tealium.prism.momentsapi

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.settings.modules.ModuleSettingsBuilder
import com.tealium.prism.momentsapi.internal.MomentsApiConfiguration

/**
 * Builder for MomentsApi module settings.
 */
class MomentsApiSettingsBuilder : ModuleSettingsBuilder<MomentsApiSettingsBuilder>(Modules.Types.MOMENTS_API) {
    
    /**
     * Sets the region for the MomentsApi.
     * 
     * @param region The MomentsApi region
     * @return The builder instance for method chaining
     */
    fun setRegion(region: MomentsApiRegion): MomentsApiSettingsBuilder = apply {
        configuration.put(MomentsApiConfiguration.KEY_REGION, region)
    }
    
    /**
     * Sets the referrer for the MomentsApi requests.
     * 
     * @param referrer The referrer URL
     * @return The builder instance for method chaining
     */
    fun setReferrer(referrer: String): MomentsApiSettingsBuilder = apply {
        configuration.put(MomentsApiConfiguration.KEY_REFERRER, referrer)
    }
}

