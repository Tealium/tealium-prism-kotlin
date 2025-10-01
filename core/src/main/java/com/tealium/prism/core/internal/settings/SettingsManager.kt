package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.seconds
import com.tealium.prism.core.api.network.NetworkHelper
import com.tealium.prism.core.api.network.ResourceCache
import com.tealium.prism.core.api.network.ResourceRefresher
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.network.ResourceCacheImpl
import com.tealium.prism.core.internal.network.ResourceRefresherImpl
import com.tealium.prism.core.internal.pubsub.CompletedDisposable
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.internal.pubsub.addTo
import com.tealium.prism.core.internal.pubsub.asObservableState
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
    private val cache: ResourceCache<DataObject>,
    private val logger: Logger
) : SettingsProvider {

    constructor(
        config: TealiumConfig,
        networkHelper: NetworkHelper,
        dataStore: DataStore,
        logger: Logger
    ) : this(
        config, networkHelper, ResourceCacheImpl(
            dataStore,
            KEY_SETTINGS,
            DataObject.Converter
        ),
        logger
    )

    private val _sdkSettingsData: Subject<DataObject> = Observables.publishSubject()
    private val _sdkSettings: ObservableState<SdkSettings>
    private val resourceRefresher: ResourceRefresher<DataObject>?

    init {
        val mergedSettings =
            mergeSettings(loadLocalSettings(), loadCachedSettings(), loadEnforcedSettings())
        logSettings(mergedSettings)

        val mergedSdkSettings = SdkSettings.fromDataObject(mergedSettings)
        _sdkSettings = Observables.stateSubject(mergedSdkSettings)

        _sdkSettingsData.forEach(::logSettings)
            .map(SdkSettings::fromDataObject)
            .subscribe(_sdkSettings)

        resourceRefresher = createResourceRefresher(
            config,
            networkHelper,
            sdkSettings.value.core.refreshInterval,
            cache,
            logger
        )
    }

    override val sdkSettings: ObservableState<SdkSettings>
        get() = _sdkSettings.asObservableState()

    private fun logSettings(settings: DataObject) {
        logger.debug(LogCategory.SETTINGS_MANAGER) {
            "Applying settings: $settings"
        }
    }

    private fun loadLocalSettings(): DataObject? {
        val localSettings = loadFromAsset(config)

        logger.trace(
            LogCategory.SETTINGS_MANAGER,
            "Settings loaded from local file: %s",
            localSettings ?: DataObject.EMPTY_OBJECT
        )

        return localSettings
    }

    private fun loadCachedSettings(): DataObject? {
        if (!config.useRemoteSettings) return null

        val cachedSettings = cache.resource

        logger.trace(
            LogCategory.SETTINGS_MANAGER,
            "Settings loaded from cache: %s",
            cachedSettings ?: DataObject.EMPTY_OBJECT
        )

        return cachedSettings
    }

    private fun loadEnforcedSettings(): DataObject {
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

        newSettingsMerged(resourceRefresher).subscribe(_sdkSettingsData)
            .addTo(subscriptions)

        refreshInterval(sdkSettings).subscribe(resourceRefresher::setRefreshInterval)
            .addTo(subscriptions)

        shouldRefresh(activities, resourceRefresher).subscribe {
            logger.debug(LogCategory.SETTINGS_MANAGER, "Refreshing remote settings")
            resourceRefresher.requestRefresh()
        }.addTo(subscriptions)

        return subscriptions
    }

    internal fun newSettingsMerged(refresher: ResourceRefresher<DataObject>): Observable<DataObject> {
        return refresher.resource
            .map { newRemoteSettings ->
                logger.debug(LogCategory.SETTINGS_MANAGER, "New SDK settings downloaded")
                logger.trace(
                    LogCategory.SETTINGS_MANAGER,
                    "Downloaded settings: %s",
                    newRemoteSettings
                )
                mergeSettings(loadLocalSettings(), newRemoteSettings, config.enforcedSdkSettings)
            }
    }

    companion object {
        const val KEY_SETTINGS = "settings"

        /**
         * Merges the given settings [DataObject]s in the order of priority that they are provided in, with
         * the lowest priority settings given first, and the highest priority settings given last.
         */
        fun mergeSettings(
            vararg settings: DataObject?
        ): DataObject {
            val nonNullSettings = settings.filterNotNull()
            if (nonNullSettings.isEmpty()) return DataObject.EMPTY_OBJECT

            val merged = nonNullSettings
                .reduce { low, high -> low.merge(high, 3) }

            return merged
        }

        internal fun loadFromAsset(config: TealiumConfig): DataObject? {
            val fileName = config.localSdkSettingsFileName ?: return null

            return try {
                config.application.assets.open(fileName).bufferedReader().use {
                    DataObject.fromString(it.readText())
                }
            } catch (ioe: IOException) {
                null
            }
        }

        internal fun shouldRefresh(
            activities: Observable<ActivityManager.ApplicationStatus>,
            refresher: ResourceRefresher<DataObject>
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
                .map { it.core.refreshInterval }
                .distinct()
        }

        internal fun createResourceRefresher(
            config: TealiumConfig,
            networkHelper: NetworkHelper,
            refreshInterval: TimeFrame,
            cache: ResourceCache<DataObject>,
            logger: Logger
        ): ResourceRefresher<DataObject>? {
            if (config.useRemoteSettings && config.sdkSettingsUrl == null) return null

            return try {
                val url = URL(config.sdkSettingsUrl)
                ResourceRefresherImpl(
                    networkHelper,
                    DataObject.Converter,
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