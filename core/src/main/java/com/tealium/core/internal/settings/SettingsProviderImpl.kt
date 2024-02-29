package com.tealium.core.internal.settings

import com.tealium.core.api.settings.SettingsProvider
import com.tealium.core.internal.SdkSettings
import com.tealium.core.internal.observables.ObservableState
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.StateSubject

/**
 * InternalSettingsProvider is an internal interface extending [SettingsProvider] to provide additional
 * methods for managing Tealium SDK settings. This interface is intended for use within the same
 * module or package and should not be exposed publicly.
 */
interface InternalSettingsProvider : SettingsProvider {

    /**
     * Updates the SDK settings with the provided [newSettings].
     *
     * @param newSettings The new SDK settings to be applied.
     */
    fun updateSdkSettings(newSettings: SdkSettings)

    fun getLastRefreshTime() : Long
    fun updateLastRefreshTime(timestamp: Long)
}

/**
 * SettingsProviderImpl is an implementation of [InternalSettingsProvider] responsible for managing
 * Tealium SDK settings. It extends [SettingsProvider] to expose settings for observation.
 *
 * @param _sdkSettingsSubject The state subject used to store and emit SDK settings updates.
 */
class SettingsProviderImpl(
    // TODO - the default needs to be from disk/cache.
    private val _sdkSettingsSubject: StateSubject<SdkSettings> =
        Observables.stateSubject(SdkSettings()),
    private var _lastRefreshTime: Long = 0L
) : InternalSettingsProvider {

    /**
     * A [ObservableState] that emits updates to the SDK settings.
     */
    override val onSdkSettingsUpdated: ObservableState<SdkSettings>
        get() = _sdkSettingsSubject.asObservableState()

    override fun updateSdkSettings(newSettings: SdkSettings) {
        _sdkSettingsSubject.onNext(newSettings)
    }

    override fun getLastRefreshTime(): Long {
        return _lastRefreshTime
    }

    override fun updateLastRefreshTime(timestamp: Long) {
        _lastRefreshTime = timestamp
    }
}