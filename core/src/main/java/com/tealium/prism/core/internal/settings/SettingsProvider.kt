package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.pubsub.ObservableState

/**
 * The [SettingsProvider] interface defines the contract for providing access to
 * SDK settings updates through the [sdkSettings] property.
 */
interface SettingsProvider {

    /**
     * An [ObservableState] that emits the latest SDK settings updates.
     */
    val sdkSettings: ObservableState<SdkSettings>
}