package com.tealium.prism.core.api.settings.modules

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.internal.modules.deeplink.DeepLinkModuleConfiguration

/**
 * A builder class used to enforce the available configuration for Deep Link handling
 */
class DeepLinkSettingsBuilder: CollectorSettingsBuilder<DeepLinkSettingsBuilder>(Modules.Types.DEEP_LINK) {

    /**
     * Sets whether or not automatic deep link handling is enabled.
     *
     * If set to `true` then Deep Links will be listened for, and its uri data/parameters will be
     * stored for the length of the session. The uri will also be passed on for further processing
     * if enabled by [setDeepLinkTraceEnabled] and [setSendDeepLinkEventEnabled]
     *
     * The default setting is `true`
     *
     * @param enabled `true` if Deep Links should automatically be handled, false
     */
    fun setAutomaticDeepLinkTrackingEnabled(enabled: Boolean) = apply {
        configuration.put(DeepLinkModuleConfiguration.KEY_AUTOMATIC_DEEPLINK_TRACKING, enabled)
    }

    /**
     * Sets whether or not to send an additional event when a Deep Link is opened.
     *
     * The default setting is `false`
     *
     * @param enabled `true` if an additional `deep_link` event should be sent upon a Deep Link being opened; else `false`
     */
    fun setSendDeepLinkEventEnabled(enabled: Boolean) = apply {
        configuration.put(DeepLinkModuleConfiguration.KEY_SEND_DEEPLINK_EVENT, enabled)
    }

    /**
     * Sets whether or not to look for Trace-specific Uri Parameters on any opened Deep Links.
     *
     * If the relevant Trace parameters are found then all trace features can be controlled via a
     * Deep Link - join, leave, force end of visit etc.
     *
     * This setting is required to be `true` for the QR Trace Tealium Tool to function.
     *
     * The default setting is `true`
     *
     * @param enabled `true` if Trace features should be allowed to be controlled by Deep Links; else `false`
     */
    fun setDeepLinkTraceEnabled(enabled: Boolean) = apply {
        configuration.put(DeepLinkModuleConfiguration.KEY_DEEPLINK_TRACE_ENABLED, enabled)
    }
}