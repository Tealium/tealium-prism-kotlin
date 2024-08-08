package com.tealium.core.internal.settings

import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.misc.ActivityManager
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.api.data.plus
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.internal.logger.LogCategory
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.internal.persistence.getTimestampMilliseconds
import java.io.IOException
import kotlin.properties.Delegates

/**
 * Manages the retrieval and merging of Tealium SDK Settings, both remote and local.
 *
 * The [SettingsManager] coordinates the loading of settings and merges the retrieved settings. It
 * also takes into account the application's lifecycle to determine when the settings should be
 * refreshed.
 *
 * @param config The Tealium configuration specifying settings retrieval details.
 * @param networkHelper The utility class providing network-related functionality.
 * @param dataStore The data store for storing and retrieving Tealium settings.
 * @param logger The logger for logging information.
 * @param sdkSettings Subject to publish updated [SdkSettings] to.
 * @param activities The manager tracking the application's lifecycle (default is [ActivityManagerImpl]).
 * @param lastRefreshTime Sets the initial lastRefreshTime; should only be used for testing
 * @param refreshTimeout Sets the timeout in milliseconds before a new refresh of the settings is allowed
 * @param timingProvider Sets the source to retrieve the current time in milliseconds
 */
class SettingsManager(
    private val config: TealiumConfig,
    private val networkHelper: NetworkHelper,
    private val dataStore: DataStore,
    private val logger: Logger,
    private val sdkSettings: StateSubject<SdkSettings> = Observables.stateSubject(
        loadInitialSettings(config, dataStore)
    ),
    private val activities: Observable<ActivityManager.ApplicationStatus> = Observables.publishSubject(),
    private var lastRefreshTime: Long = 0L,
    private var refreshTimeout: Long = REFRESH_INTERVAL_MILLIS,
    private val timingProvider: () -> Long = ::getTimestampMilliseconds
) : SettingsProvider {

    init {
        onSdkSettingsUpdated.subscribe {
            logger.debug?.log(LogCategory.SETTINGS_MANAGER, "Applying settings: ${it.asTealiumValue()}")
        }
    }

    private var localSettings: TealiumBundle? = null

    override val onSdkSettingsUpdated: ObservableState<SdkSettings>
        get() = sdkSettings.asObservableState()

    private var remoteSettings: TealiumBundle? by Delegates.observable(
        initialValue = loadCachedSettings(),
        onChange = { _, _, _ ->
            sdkSettings.onNext(loadSettings())
        }
    )

    fun loadSettings(): SdkSettings {
        val localSettings = loadLocalSettings()

        logger.trace?.log(LogCategory.SETTINGS_MANAGER, "Settings loaded from local file: $localSettings")

        return mergeSettings(config, remoteSettings, localSettings)
    }

    private fun loadCachedSettings(): TealiumBundle? {
        return loadFromCache(config, dataStore).also { settings ->
            logger.trace?.log(LogCategory.SETTINGS_MANAGER, "Settings loaded from cache: $settings")

            if (settings == null) {
                // parse failure; remove
                removeFromDataStore(dataStore)
            }
        }
    }

    fun refreshRemote() {
        logger.debug?.log(
            LogCategory.SETTINGS_MANAGER,
            "Refreshing remote settings"
        )
        val currentTime = timingProvider()
        val url = config.sdkSettingsUrl
        if (!config.useRemoteSettings || url == null || !isTimedOut(
                currentTime,
                getLastRefreshTime(),
                refreshTimeout
            )
        ) {
            return
        }

        setLastRefreshTime(currentTime)

        fetchRemoteSettings(
            url,
            loadCachedEtag(dataStore)?.getString(),
            networkHelper
        ) { newRemoteSettings ->
            if (newRemoteSettings == null) {
                return@fetchRemoteSettings
            }

            val etag = newRemoteSettings.get("etag")
            addToDataStore(etag, newRemoteSettings, dataStore)

            logger.debug?.log(
                LogCategory.SETTINGS_MANAGER,
                "New SDK settings downloaded"
            )
            logger.trace?.log(
                LogCategory.SETTINGS_MANAGER,
                "Downloaded settings: $newRemoteSettings"
            )

            remoteSettings = newRemoteSettings
        }
    }

    fun subscribeToActivityUpdates(): Disposable {
        return activities.subscribe { newStatus ->
            when (newStatus) {
                is ActivityManager.ApplicationStatus.Init -> {
                    refreshRemote()
                }
                is ActivityManager.ApplicationStatus.Foregrounded -> {
                    val lastRefreshTime = getLastRefreshTime()
                    val currentTime = timingProvider()
                    if (isTimedOut(currentTime, lastRefreshTime, refreshTimeout)) {
                        refreshRemote()
                    }
                }

                else -> {
                    // do nothing
                }
            }
        }
    }

    private fun loadLocalSettings(): TealiumBundle? {
        if (localSettings != null) return localSettings

        return loadFromAsset(config)?.also {
            localSettings = it
        }
    }

    internal fun setLastRefreshTime(timestamp: Long = timingProvider()) {
        lastRefreshTime = timestamp
    }

    internal fun getLastRefreshTime(): Long {
        return lastRefreshTime
    }

    companion object {
        /**
         * The refresh interval for settings in milliseconds (15 minutes by default).
         */
        internal const val REFRESH_INTERVAL_MILLIS = 15 * 60 * 1000L // 15 minutes
        internal const val KEY_SDK_SETTINGS = "sdk_settings"
        internal const val KEY_SETTINGS_ETAG = "settings_etag"

        /**
         * Reads the settings file from a configured Asset, given by [TealiumConfig.localSdkSettingsFileName]
         * and merges any cached settings from the given [dataStore] into it.
         * The result is subsequently merged with any settings configured on the given [config]
         *
         * The result of this method is only valid for the initial startup of the SDK, and continual
         * updates should be requested from [SettingsManager.onSdkSettingsUpdated] as this will provide
         * the subscriber with the merged settings from any newly updated remote settings if configured.
         */
        fun loadInitialSettings(config: TealiumConfig, dataStore: DataStore): SdkSettings {
            val localSettings = loadFromAsset(config)
            val cachedSettings = loadFromCache(config, dataStore)
            return mergeSettings(config, cachedSettings, localSettings)
        }

        /**
         * Merges the retrieved remote and local settings.
         */
        fun mergeSettings(
            config: TealiumConfig,
            remoteSettings: TealiumBundle? = null,
            localSettings: TealiumBundle? = null
        ): SdkSettings {
            val localBundle = localSettings?.asTealiumValue()?.getBundle()
            val remoteBundle = remoteSettings?.asTealiumValue()?.getBundle()

            val merged = if (remoteBundle != null && localBundle != null) {
                localBundle + remoteBundle
            } else remoteBundle ?: localBundle ?: TealiumBundle.EMPTY_BUNDLE

            val mergedProgrammaticSettings = mergeProgrammaticSettings(config, merged)

            return mergedProgrammaticSettings.asTealiumValue().let {
                SdkSettings.Deserializer.deserialize(it)
            } ?: SdkSettings()
        }

        /**
         * Merges the programmatic settings provided by modules in the TealiumConfig.
         */
        private fun mergeProgrammaticSettings(
            config: TealiumConfig,
            sdkBundle: TealiumBundle
        ): TealiumBundle {
            var merged = sdkBundle
            config.modulesSettings.forEach { module ->
                val bundle = merged.getBundle(module.key)
                val moduleBundle = module.value.bundle
                val mergedBundle = if (bundle != null) {
                    bundle + moduleBundle
                } else {
                    moduleBundle
                }
                merged = merged.buildUpon()
                    .put(key = module.key, mergedBundle)
                    .getBundle()
            }

            return merged
        }

        internal fun loadCachedEtag(dataStore: DataStore): TealiumValue? {
            return dataStore.get(KEY_SETTINGS_ETAG)
        }

        internal fun addToDataStore(
            etag: TealiumValue?,
            settings: TealiumBundle,
            dataStore: DataStore
        ) {
            dataStore.edit().apply {
                put(KEY_SETTINGS_ETAG, etag ?: TealiumValue.NULL, Expiry.FOREVER)
                put(KEY_SDK_SETTINGS, settings.asTealiumValue(), Expiry.FOREVER)
            }.commit()
        }

        internal fun removeFromDataStore(dataStore: DataStore) {
            dataStore.edit().apply {
                remove(KEY_SETTINGS_ETAG)
                remove(KEY_SDK_SETTINGS)
            }.commit()
        }

        internal fun loadFromAsset(config: TealiumConfig): TealiumBundle? {
            val fileName = config.localSdkSettingsFileName ?: return null

            return try {
                config.application.assets.open(fileName).bufferedReader().use {
                    TealiumBundle.fromString(it.readText())
                }
            } catch (ioe: IOException) {
                null
            }
        }

        internal fun loadFromCache(config: TealiumConfig, dataStore: DataStore): TealiumBundle? {
            if (!config.useRemoteSettings) {
                return null
            }

            return dataStore.get(KEY_SDK_SETTINGS)?.getBundle()
        }

        internal fun fetchRemoteSettings(
            urlString: String,
            etag: String?,
            network: NetworkHelper,
            completion: (TealiumBundle?) -> Unit
        ) {
            network.getTealiumBundle(urlString, etag) { bundle ->
                if (bundle == null) {
                    completion(null)
                    return@getTealiumBundle
                }

                completion(bundle)
            }
        }

        internal fun isTimedOut(
            timestamp: Long,
            lastRefreshTime: Long,
            refreshTimeout: Long
        ): Boolean {
            return refreshTimeout < timestamp - lastRefreshTime
        }
    }
}