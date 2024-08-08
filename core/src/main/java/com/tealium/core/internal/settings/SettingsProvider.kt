package com.tealium.core.internal.settings

import com.tealium.core.api.pubsub.ObservableState

/**
 * The [SettingsProvider] interface defines the contract for providing access to
 * SDK settings updates through the [onSdkSettingsUpdated] property.
 */
interface SettingsProvider {

    /**
     * An [ObservableState] that emits the latest SDK settings updates.
     */
    val onSdkSettingsUpdated: ObservableState<SdkSettings>
}