package com.tealium.core.api.settings

import com.tealium.core.api.TealiumConfig
import com.tealium.core.internal.modules.collect.CollectDispatcherSettings

/**
 * Enables the configuration, at runtime, of the Collect Dispatcher.
 *
 * Note. Any configuration set here will override any configuration provided by the local or remote
 * settings files and will no longer be overridable remotely.
 */
class CollectDispatcherSettingsBuilder : ModuleSettingsBuilder() {

    /**
     * Sets the url to use when sending individual events
     */
    fun setUrl(url: String) = apply {
        builder.put(CollectDispatcherSettings.KEY_COLLECT_URL, url)
    }

    /**
     * Sets the url to use when sending batches of events
     */
    fun setBatchUrl(url: String) = apply {
        builder.put(CollectDispatcherSettings.KEY_COLLECT_BATCH_URL, url)
    }

    /**
     * Sets the Tealium profile to use when sending the event. Use this when the profile differs to
     * the one configured in the [TealiumConfig]
     */
    fun setProfile(profile: String) = apply {
        builder.put(CollectDispatcherSettings.KEY_COLLECT_PROFILE, profile)
    }

    /**
     * Sets the domain to use in place of the default single and batch URLs.
     *
     * This setting will not override the domain of any values provided in [setUrl] and [setBatchUrl]
     */
    fun setDomain(domain: String) = apply {
        builder.put(CollectDispatcherSettings.KEY_COLLECT_DOMAIN, domain)
    }
}