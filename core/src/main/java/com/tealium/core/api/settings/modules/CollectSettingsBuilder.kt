package com.tealium.core.api.settings.modules

import com.tealium.core.api.Modules
import com.tealium.core.api.TealiumConfig
import com.tealium.core.internal.modules.collect.CollectModuleConfiguration

/**
 * Enables the configuration, at runtime, of the Collect Module.
 *
 * Note. Any configuration set here will override any configuration provided by the local or remote
 * settings files and will no longer be overridable remotely.
 */
class CollectSettingsBuilder :
    DispatcherSettingsBuilder<CollectSettingsBuilder>(Modules.Types.COLLECT),
    MultipleInstancesModuleSettingsBuilder<CollectSettingsBuilder> {

    override fun setModuleId(moduleId: String): CollectSettingsBuilder =
        setModuleIdInternal(moduleId)

    /**
     * Sets the url to use when sending individual events
     */
    fun setUrl(url: String) = apply {
        configuration.put(CollectModuleConfiguration.KEY_COLLECT_URL, url)
    }

    /**
     * Sets the url to use when sending batches of events
     */
    fun setBatchUrl(url: String) = apply {
        configuration.put(CollectModuleConfiguration.KEY_COLLECT_BATCH_URL, url)
    }

    /**
     * Sets the Tealium profile to use when sending the event. Use this when the profile differs to
     * the one configured in the [TealiumConfig]
     */
    fun setProfile(profile: String) = apply {
        configuration.put(CollectModuleConfiguration.KEY_COLLECT_PROFILE, profile)
    }

    /**
     * Sets the domain to use in place of the default single and batch URLs.
     *
     * This option will not override the domain of any values provided in [setUrl] and [setBatchUrl]
     */
    fun setDomain(domain: String) = apply {
        configuration.put(CollectModuleConfiguration.KEY_COLLECT_DOMAIN, domain)
    }
}