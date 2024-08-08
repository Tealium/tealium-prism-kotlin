package com.tealium.core.api.settings

import com.tealium.core.api.TealiumConfig
import com.tealium.core.internal.modules.VisitorServiceImpl
import com.tealium.core.internal.modules.VisitorServiceSettings

/**
 * Enables the configuration, at runtime, of the VisitorService.
 *
 * Note. Any configuration set here will override any configuration provided by the local or remote
 * settings files and will no longer be overridable remotely.
 */
class VisitorServiceSettingsBuilder: ModuleSettingsBuilder(VisitorServiceImpl.moduleName) {

    /**
     * Sets the URL template to use when fetching the latest visitor profile. Available template
     * params are as follows:
     *  - {{account}}
     *  - {{profile}}
     *  - {{visitorId}}
     * These will be substituted at runtime for the appropriate value. The "account" will be provided
     * by the [TealiumConfig] and the profile will be provided by either the [TealiumConfig] or the
     * value set at [setProfile] (if configured)
     */
    fun setUrlTemplate(url: String) {
        builder.put(VisitorServiceSettings.DEFAULT_VISITOR_SERVICE_TEMPLATE, url)
    }

    /**
     * Sets an override profile to use instead of the one provided by the [TealiumConfig]
     */
    fun setProfile(profile: String) {
        builder.put(VisitorServiceSettings.VISITOR_SERVICE_OVERRIDE_PROFILE, profile)
    }

    /**
     * Sets the interval to use when updating the visitor profile
     */
    // TODO - change signature, or rename this to specify the unit
    fun setRefreshIntervalSeconds(interval: Int) {
        builder.put(VisitorServiceSettings.VISITOR_SERVICE_REFRESH_INTERVAL, interval)
    }
}