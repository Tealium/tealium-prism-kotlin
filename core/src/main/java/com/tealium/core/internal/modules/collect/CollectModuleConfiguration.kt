package com.tealium.core.internal.modules.collect

import android.net.Uri
import com.tealium.core.api.data.DataObject
import java.net.MalformedURLException
import java.net.URL

/**
 * Carries all available configuration for the Collect Module
 *
 * @param url The endpoint to dispatch single events to
 * @param batchUrl The endpoint to dispatch batched events to
 * @param profile Optional - Tealium profile name to override on the payload
 */
data class CollectModuleConfiguration(
    val url: URL = URL(DEFAULT_COLLECT_URL),
    val batchUrl: URL = URL(DEFAULT_COLLECT_BATCH_URL),
    val profile: String? = null
) {
    companion object Companion {
        const val MAX_BATCH_SIZE = 10
        const val DEFAULT_COLLECT_URL = "https://collect.tealiumiq.com/event"
        const val DEFAULT_COLLECT_BATCH_URL = "https://collect.tealiumiq.com/bulk-event"

        /**
         * Current module configuration from Mobile Data Source front-end.
         * TODO - Validate that these are final
         *
         * "collect": {
         *   "enabled": true,
         *   "configuration": {
         *      "dispatch_profile": "...",
         *      "single_dispatch_url": "https://collect.tealiumiq.com/event/",
         *      "batch_dispatch_url": "https://collect.tealiumiq.com/bulk-event/",
         *      "dispatch_domain": "collect.tealiumiq.com"
         *   }
         * }
         */
        const val KEY_COLLECT_DOMAIN = "dispatch_domain"
        const val KEY_COLLECT_URL = "single_dispatch_url"
        const val KEY_COLLECT_BATCH_URL = "batch_dispatch_url"
        const val KEY_COLLECT_PROFILE = "dispatch_profile"

        fun fromDataObject(configuration: DataObject): CollectModuleConfiguration? {
            val profile = configuration.getString(KEY_COLLECT_PROFILE)
            val url = configuration.parseUrl(KEY_COLLECT_URL, DEFAULT_COLLECT_URL)
            val batchUrl = configuration.parseUrl(KEY_COLLECT_BATCH_URL, DEFAULT_COLLECT_BATCH_URL)

            if (url == null || batchUrl == null) return null

            return CollectModuleConfiguration(
                url = url, batchUrl = batchUrl, profile = profile
            )
        }

        private fun configureDomain(url: String, domain: String?): URL? {
            if (domain == null) {
                return url.parseUrl()
            }

            return Uri.parse(url).buildUpon()
                .authority(domain)
                .build()
                .toString()
                .parseUrl()
        }

        private fun DataObject.parseUrl(key: String, default: String): URL? {
            val urlString = getString(key)
            if (urlString != null) {
                // url override set; do not fall back to defaults.
                return urlString.parseUrl()
            }

            return configureDomain(default, getString(KEY_COLLECT_DOMAIN))
        }

        private fun String.parseUrl(): URL? = try {
            URL(toString())
        } catch (e: MalformedURLException) {
            null
        }
    }
}