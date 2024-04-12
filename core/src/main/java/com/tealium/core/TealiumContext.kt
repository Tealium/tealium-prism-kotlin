package com.tealium.core

import android.content.Context
import com.tealium.core.api.*
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.internal.settings.SettingsProvider
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.settings.CoreSettings

class TealiumContext(
    val context: Context,
    val config: TealiumConfig,
    val logger: Logger,
    // TODO - find a better place to access this?
    visitorId: String, // todo
    val storageProvider: ModuleStoreProvider,
    val network: NetworkUtilities,
    settingsProvider: SettingsProvider,
    val tracker: Tracker,
    val schedulers: Schedulers,
    val activityManager: ActivityManager
) {
    private val _visitorId = visitorId
    val onCoreSettings: Observable<CoreSettings> =
        settingsProvider.onSdkSettingsUpdated.map { settings ->
            settings.coreSettings
        }
    val visitorId: String
        get() {
            //TODO()
            //tealium.
            return _visitorId
        }
}
