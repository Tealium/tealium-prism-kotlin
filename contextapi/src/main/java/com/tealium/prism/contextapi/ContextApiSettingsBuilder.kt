package com.tealium.prism.contextapi

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.settings.modules.ModuleSettingsBuilder
import com.tealium.prism.contextapi.internal.ContextApiConfiguration

/**
 * Builder for ContextApi module settings.
 */
class ContextApiSettingsBuilder : ModuleSettingsBuilder<ContextApiSettingsBuilder>(Modules.Types.CONTEXT_API) {
    
    /**
     * Sets the region for the ContextApi.
     * 
     * @param region The ContextApi region
     * @return The builder instance for method chaining
     */
    fun setRegion(region: ContextApiRegion): ContextApiSettingsBuilder = apply {
        configuration.put(ContextApiConfiguration.KEY_REGION, region)
    }
    
    /**
     * Sets the referrer for the ContextApi requests.
     * 
     * @param referrer The referrer URL
     * @return The builder instance for method chaining
     */
    fun setReferrer(referrer: String): ContextApiSettingsBuilder = apply {
        configuration.put(ContextApiConfiguration.KEY_REFERRER, referrer)
    }
}

