package com.tealium.core.internal.settings

import com.tealium.core.TealiumConfig
import com.tealium.core.api.DataStore
import com.tealium.core.api.Expiry
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.internal.SdkSettings
import java.io.IOException

/**
 * SettingsRetriever is responsible for retrieving and managing Tealium settings,
 * both remotely and locally. It allows for the retrieval of SDK settings for
 * core and module-specific settings.
 *
 * @param config The Tealium configuration to use for settings retrieval.
 * @param network The NetworkUtilities instance to perform network operations.
 * @param settingsDataStore The DataStore to cache settings.
 */
class SettingsRetriever(
    private val config: TealiumConfig,
    private val network: NetworkUtilities,
    private val settingsDataStore: DataStore,
    private val logger: Logger,
) {

    // Remote - needs updating to new mobile endpoint
    private val urlString: String?
        get() = config.sdkSettingsUrl // this needs backend endpoint

    // Local
    private val assetString: String?
        get() = config.localSdkSettingsFileName

    fun fetchRemoteSettings(completion: (SdkSettings?) -> Unit) {
        val url = urlString
        if (url == null) {
            completion(null)
            return
        }

        network.networkHelper.getTealiumBundle(url, loadCachedEtag()?.getString()) { bundle ->
            if (bundle == null) {
                completion(null)
                return@getTealiumBundle
            }

            val etag = bundle.get("etag")
            val newSettings = SdkSettings.Deserializer.deserialize(bundle.asTealiumValue())

            if (newSettings == null) {
                completion(null)
                return@getTealiumBundle
            }

            logger.debug?.log(
                "SettingsRetriever",
                "Remote Settings retrieved: $newSettings"
            )
            addToDataStore(etag, newSettings)
            completion(newSettings)
        }
    }

    fun loadLocalSettings(): SdkSettings? {
        return assetString?.let { fileName ->
            loadFromAsset(fileName)?.let {
                val bundle = TealiumBundle.fromString(it) ?: return null

                //To core/module settings
                val sdkSettings =
                    SdkSettings.Deserializer.deserialize(bundle.asTealiumValue()) ?: SdkSettings()
                logger.debug?.log(
                    "SettingsRetriever",
                    "Local Settings loaded: $sdkSettings"
                )

                sdkSettings
            }
        }
    }

    private fun loadFromAsset(fileName: String): String? {
        return try {
            config.application.assets.open(fileName).bufferedReader().use {
                return it.readText()
            }
        } catch (ioe: IOException) {
            logger.debug?.log("SettingsRetriever", "Asset not found ($fileName)")
            null
        }
    }

    internal fun loadFromCache(): SdkSettings? {
        val value = settingsDataStore.get(KEY_SDK_SETTINGS) ?: return null

        return SdkSettings.Deserializer.deserialize(value).also { settings ->
            if (settings == null) {
                // parse failure; remove
                removeFromDataStore()
            }
        }
    }

    internal fun loadCachedEtag(): TealiumValue? {
        return settingsDataStore.get(KEY_SETTINGS_ETAG)
    }

    internal fun addToDataStore(etag: TealiumValue?, settings: SdkSettings) {
        settingsDataStore.edit().apply {
            put(KEY_SETTINGS_ETAG, etag ?: TealiumValue.NULL, Expiry.FOREVER)
            put(KEY_SDK_SETTINGS, settings.asTealiumValue(), Expiry.FOREVER)
        }.commit()
    }

    private fun removeFromDataStore() {
        settingsDataStore.edit().apply {
            remove(KEY_SETTINGS_ETAG)
            remove(KEY_SDK_SETTINGS)
        }.commit()
    }

    companion object {
        const val KEY_SDK_SETTINGS = "sdk_settings"
        const val KEY_SETTINGS_ETAG = "settings_etag"
    }
}