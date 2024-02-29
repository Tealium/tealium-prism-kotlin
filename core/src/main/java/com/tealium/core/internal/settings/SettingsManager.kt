package com.tealium.core.internal.settings

import com.tealium.core.TealiumConfig
import com.tealium.core.api.ActivityManager
import com.tealium.core.api.ActivityManagerImpl
import com.tealium.core.api.DataStore
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.plus
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.internal.SdkSettings
import kotlin.properties.Delegates

/**
 * Manages the retrieval and merging of Tealium SDK Settings, both remote and local.
 *
 * The [SettingsManager] coordinates the loading of settings through a [SettingsRetriever],
 * merges the retrieved settings, and updates an [InternalSettingsProvider] with the final
 * settings. It also takes into account the application's lifecycle to determine when
 * the settings should be refreshed.
 *
 * @param config The Tealium configuration specifying settings retrieval details.
 * @param network The utility class providing network-related functionality.
 * @param settingsDataStore The data store for storing and retrieving Tealium settings.
 * @param settingsProvider The provider for managing the internal SDK settings.
 * @param logger The logger for logging information.
 * @param activityManager The manager tracking the application's lifecycle (default is [ActivityManagerImpl]).
 * @param settingsRetriever The retriever responsible for fetching remote and local settings.
 */
class SettingsManager(
    private val config: TealiumConfig,
    private val network: NetworkUtilities,
    private val settingsDataStore: DataStore,
    private val settingsProvider: InternalSettingsProvider,
    private val logger: Logger,
    private val activityManager: ActivityManager = ActivityManagerImpl(),
    private val settingsRetriever: SettingsRetriever = SettingsRetriever(
        config,
        network,
        settingsDataStore,
        logger
    ),
) {

    private var remoteSettings: SdkSettings? by Delegates.observable(
        initialValue = loadCachedSettings(),
        onChange = { _, _, _ ->
            _currentSdkSettings = loadSettings()
        }
    )
    private var _currentSdkSettings: SdkSettings = loadSettings()
    val currentSdkSettings: SdkSettings
        get() = _currentSdkSettings

    init {
        fetchRemote()
        refreshSettings()
    }

    internal fun loadSettings(): SdkSettings {
        logger.debug?.log("SettingsRetriever", "Loading Settings")

        val localSettings = settingsRetriever.loadLocalSettings()
        val settings = mergeSettings(remoteSettings, localSettings)

        // notify of merged remote/local settings
        settingsProvider.updateSdkSettings(settings)
        return settings
    }

    private fun loadCachedSettings(): SdkSettings? {
        if (!config.useRemoteSettings) {
            return null
        }

        return settingsRetriever.loadFromCache().also {
            logger.debug?.log("SettingsRetriever", "Settings loaded from cache")
        }
    }

    private fun fetchRemote() {
        if (!config.useRemoteSettings) {
            return
        }

        settingsProvider.updateLastRefreshTime(System.currentTimeMillis())
        settingsRetriever.fetchRemoteSettings { newRemoteSettings ->
            if (newRemoteSettings == null) {
                return@fetchRemoteSettings
            }

            logger.debug?.log("SettingsRetriever", "Received new remote settings: $newRemoteSettings")
            remoteSettings = newRemoteSettings
        }
    }

    /**
     * Merges the retrieved remote and local settings.
     */
    internal fun mergeSettings(
        remoteSettings: SdkSettings? = null,
        localSettings: SdkSettings? = null
    ): SdkSettings {
        val localBundle = localSettings?.asTealiumValue()?.getBundle()
        val remoteBundle = remoteSettings?.asTealiumValue()?.getBundle()

        val merged = if (remoteBundle != null && localBundle != null) {
            localBundle + remoteBundle
        } else remoteBundle ?: localBundle

        val mergedProgrammaticSettings = mergeProgrammaticSettings(merged)
        val finalSettings = mergedProgrammaticSettings?.asTealiumValue()
            ?.let { SdkSettings.Deserializer.deserialize(it) } ?: SdkSettings()

        logger.debug?.log("SettingsRetriever", "Settings Merged: $finalSettings")
        return finalSettings
    }

    /**
     * Merges the programmatic settings provided by modules in the TealiumConfig.
     */
    private fun mergeProgrammaticSettings(sdkBundle: TealiumBundle?): TealiumBundle? {
        var merged = sdkBundle
        config.modulesSettings.forEach { module ->
            val bundle = merged?.getBundle(module.key)
            val moduleBundle = module.value.bundle
            merged = if (bundle != null) {
                merged?.copy {
                    put(module.key, bundle + moduleBundle)
                }

            } else {
                merged?.copy {
                    put(module.key, moduleBundle)
                }
            }
        }

        return merged
    }

    private fun refreshSettings() {
        activityManager.applicationStatus.subscribe { newStatus ->
            when (newStatus) {
                ActivityManager.ApplicationStatus.Init -> {
                    logger.debug?.log(
                        "SettingsRetriever",
                        "Loading Settings on ApplicationStatus.Init"
                    )
                    fetchRemote()
                }

                ActivityManager.ApplicationStatus.Foregrounded -> {
                    val lastRefreshTime = settingsProvider.getLastRefreshTime()
                    if (System.currentTimeMillis() - lastRefreshTime >= REFRESH_INTERVAL_MILLIS) {
                        logger.debug?.log(
                            "SettingsRetriever",
                            "Refreshing Settings on ApplicationStatus.Foregrounded"
                        )
                        fetchRemote()
                    }
                }

                else -> {
                    // do nothing
                }
            }
        }
    }

    companion object {
        /**
         * The refresh interval for settings in milliseconds (15 minutes by default).
         */
        private const val REFRESH_INTERVAL_MILLIS = 15 * 60 * 1000L // 15 minutes
    }
}