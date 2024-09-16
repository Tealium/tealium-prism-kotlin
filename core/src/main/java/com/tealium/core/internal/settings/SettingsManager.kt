package com.tealium.core.internal.settings

import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.plus
import com.tealium.core.api.logger.AlternateLogger
import com.tealium.core.api.misc.ActivityManager
import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.misc.TimeFrameUtils.seconds
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.network.ResourceCache
import com.tealium.core.api.network.ResourceRefresher
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.internal.logger.LogCategory
import com.tealium.core.internal.network.ResourceCacheImpl
import com.tealium.core.internal.network.ResourceRefresherImpl
import com.tealium.core.internal.pubsub.CompletedDisposable
import com.tealium.core.internal.pubsub.DisposableContainer
import com.tealium.core.internal.pubsub.addTo
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

/**
 * Manages the retrieval and merging of Tealium SDK Settings, both remote and local.
 *
 * The [SettingsManager] coordinates the loading of settings and merges the retrieved settings. It
 * also takes into account the application's lifecycle to determine when the settings should be
 * refreshed.
 *
 * @param config The Tealium configuration specifying settings retrieval details.
 * @param networkHelper The utility class providing network-related functionality.
 * @param cache The [ResourceCache] for storing and retrieving Tealium settings.
 * @param logger The logger for logging information.
 */
class SettingsManager(
    private val config: TealiumConfig,
    private val networkHelper: NetworkHelper,
    private val cache: ResourceCache<TealiumBundle>,
    private val logger: AlternateLogger
) : SettingsProvider {

    constructor(
        config: TealiumConfig,
        networkHelper: NetworkHelper,
        dataStore: DataStore,
        logger: AlternateLogger
    ) : this(
        config, networkHelper, ResourceCacheImpl(
            dataStore,
            KEY_SETTINGS,
            TealiumBundle.BundleDeserializer
        ),
        logger
    )

    private var localSettings: TealiumBundle?
    private val _sdkSettings: StateSubject<SdkSettings>
    private val resourceRefresher: ResourceRefresher<TealiumBundle>?

    init {
        localSettings = loadLocalSettings()

        val mergedSettings =
            mergeSettings(localSettings, loadCachedSettings(), loadEnforcedSettings())
        _sdkSettings = Observables.stateSubject(mergedSettings)

        _sdkSettings.subscribe {
            logger.debug(
                LogCategory.SETTINGS_MANAGER,
                "Applying settings: %s",
                it.asTealiumValue()
            )
        }

        resourceRefresher = createResourceRefresher(
            config,
            networkHelper,
            mergedSettings.coreSettings.refreshInterval,
            cache,
            logger
        )
    }

    override val sdkSettings: ObservableState<SdkSettings>
        get() = _sdkSettings.asObservableState()

    private fun loadLocalSettings(): TealiumBundle? {
        val localSettings = loadFromAsset(config)

        logger.trace(
            LogCategory.SETTINGS_MANAGER,
            "Settings loaded from local file: %s",
            localSettings ?: TealiumBundle.EMPTY_BUNDLE
        )

        return localSettings
    }

    private fun loadCachedSettings(): TealiumBundle? {
        if (!config.useRemoteSettings) return null

        val cachedSettings = cache.resource

        logger.trace(
            LogCategory.SETTINGS_MANAGER,
            "Settings loaded from cache: %s",
            cachedSettings ?: TealiumBundle.EMPTY_BUNDLE
        )

        return cachedSettings
    }

    private fun loadEnforcedSettings(): TealiumBundle {
        val enforcedSettings = config.enforcedSdkSettings

        logger.trace(
            LogCategory.SETTINGS_MANAGER,
            "Settings loaded from config: %s",
            enforcedSettings
        )

        return enforcedSettings
    }

    fun subscribeToActivityUpdates(activities: Observable<ActivityManager.ApplicationStatus>): Disposable {
        if (resourceRefresher == null) return CompletedDisposable

        val subscriptions = DisposableContainer()

        newSettingsMerged(resourceRefresher).subscribe(_sdkSettings)
            .addTo(subscriptions)

        refreshInterval(sdkSettings).subscribe(resourceRefresher::setRefreshInterval)
            .addTo(subscriptions)

        shouldRefresh(activities, resourceRefresher).subscribe {
            logger.debug(LogCategory.SETTINGS_MANAGER, "Refreshing remote settings")
            resourceRefresher.requestRefresh()
        }.addTo(subscriptions)

        return subscriptions
    }

    internal fun newSettingsMerged(refresher: ResourceRefresher<TealiumBundle>): Observable<SdkSettings> {
        return refresher.resource
            .map { newRemoteSettings ->
                logger.debug(LogCategory.SETTINGS_MANAGER, "New SDK settings downloaded")
                logger.trace(
                    LogCategory.SETTINGS_MANAGER,
                    "Downloaded settings: %s",
                    newRemoteSettings
                )
                mergeSettings(localSettings, newRemoteSettings, config.enforcedSdkSettings)
            }
    }

    companion object {
        const val KEY_SETTINGS = "settings"

        /**
         * Merges the given settings bundles in the order of priority that they are provided in, with
         * the lowest priority bundle given first, and the highest priority bundle given last.
         */
        fun mergeSettings(
            vararg settings: TealiumBundle?
        ): SdkSettings {
            val nonNullSettings = settings.filterNotNull()
            if (nonNullSettings.isEmpty()) return SdkSettings()

            val merged = nonNullSettings
                .reduce(::mergeSettingsBundles)

            return SdkSettings.Deserializer.deserialize(merged.asTealiumValue())
                ?: SdkSettings()
        }

        /**
         * Merges two settings bundles together.
         *
         * Key clashes at the top level (i.e. module id) of the given [TealiumBundle]s will be "merged".
         * That is, values from both [TealiumBundle]s will appear in the result.
         *
         * Key clashes in deeper levels will simply prefer the higher-priority settings according to
         * the [TealiumBundle.plus] operator.
         */
        private fun mergeSettingsBundles(
            lowerPriority: TealiumBundle,
            higherPriority: TealiumBundle
        ): TealiumBundle {
            return lowerPriority.copy {
                higherPriority.forEach { (id, settings) ->
                    val higherPrioritySettings = settings.getBundle()
                        ?: return@forEach

                    val lowerPrioritySettings =
                        lowerPriority.getBundle(id) ?: TealiumBundle.EMPTY_BUNDLE

                    put(id, lowerPrioritySettings + higherPrioritySettings)
                }
            }
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

        internal fun shouldRefresh(
            activities: Observable<ActivityManager.ApplicationStatus>,
            refresher: ResourceRefresher<TealiumBundle>
        ): Observable<Unit> {
            return activities.filter { newStatus ->
                (newStatus is ActivityManager.ApplicationStatus.Init
                        || newStatus is ActivityManager.ApplicationStatus.Foregrounded)
                        && refresher.shouldRefresh
            }.map { }
        }

        internal fun refreshInterval(
            sdkSettings: Observable<SdkSettings>,
        ): Observable<TimeFrame> {
            return sdkSettings
                .map { it.coreSettings.refreshInterval }
                .distinct()
        }

        internal fun createResourceRefresher(
            config: TealiumConfig,
            networkHelper: NetworkHelper,
            refreshInterval: TimeFrame,
            cache: ResourceCache<TealiumBundle>,
            logger: AlternateLogger
        ): ResourceRefresher<TealiumBundle>? {
            if (config.useRemoteSettings && config.sdkSettingsUrl == null) return null

            return try {
                val url = URL(config.sdkSettingsUrl)
                ResourceRefresherImpl(
                    networkHelper,
                    TealiumBundle.BundleDeserializer,
                    ResourceRefresher.Parameters(
                        KEY_SETTINGS,
                        url,
                        refreshInterval,
                        20.seconds
                    ),
                    cache,
                    logger = logger
                )
            } catch (ignore: MalformedURLException) {
                null
            }
        }
    }
}