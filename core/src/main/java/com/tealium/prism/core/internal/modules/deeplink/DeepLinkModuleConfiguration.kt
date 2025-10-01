package com.tealium.prism.core.internal.modules.deeplink

import com.tealium.prism.core.api.data.DataObject

data class DeepLinkModuleConfiguration(
    val automaticDeepLinkTracking: Boolean = DEFAULT_AUTOMATIC_DEEPLINK_TRACKING,
    val sendDeepLinkEvent: Boolean = DEFAULT_SEND_DEEPLINK_EVENT,
    val deepLinkTraceEnabled: Boolean = DEFAULT_DEEPLINK_TRACE_ENABLED
) {
    companion object Companion {
        const val KEY_AUTOMATIC_DEEPLINK_TRACKING = "automatic_deep_link_tracking"
        const val DEFAULT_AUTOMATIC_DEEPLINK_TRACKING = true
        const val KEY_SEND_DEEPLINK_EVENT = "send_deep_link_event"
        const val DEFAULT_SEND_DEEPLINK_EVENT = false
        const val KEY_DEEPLINK_TRACE_ENABLED = "deep_link_trace_enabled"
        const val DEFAULT_DEEPLINK_TRACE_ENABLED = true

        fun fromDataObject(dataObject: DataObject) : DeepLinkModuleConfiguration {
            val automaticDeepLinkTracking = dataObject.getBoolean(KEY_AUTOMATIC_DEEPLINK_TRACKING)
                ?: DEFAULT_AUTOMATIC_DEEPLINK_TRACKING
            val sendDeepLinkEvent = dataObject.getBoolean(KEY_SEND_DEEPLINK_EVENT)
                ?: DEFAULT_SEND_DEEPLINK_EVENT
            val deepLinkTraceEnabled = dataObject.getBoolean(KEY_DEEPLINK_TRACE_ENABLED)
                ?: DEFAULT_DEEPLINK_TRACE_ENABLED

            return DeepLinkModuleConfiguration(
                automaticDeepLinkTracking,
                sendDeepLinkEvent,
                deepLinkTraceEnabled
            )
        }
    }
}