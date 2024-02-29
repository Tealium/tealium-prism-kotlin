package com.tealium.core

import android.content.Context
import com.tealium.core.api.*
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.api.settings.SettingsProvider
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.settings.CoreSettings

class TealiumContext(
    val context: Context,
    val config: TealiumConfig,
    // TODO
    val dataLayer: DataLayer,
    val logger: Logger,
    // TODO - find a better place to access this?
    visitorId: String, // todo
    val storageProvider: ModuleStoreProvider,
    val network: NetworkUtilities,
    private val settingsProvider: SettingsProvider,
    private val tealium: Tealium
) {
    private val _visitorId = visitorId
    val onCoreSettings: Observable<CoreSettings>
        get() = settingsProvider.onSdkSettingsUpdated.map { settings ->
            settings.coreSettings
        }
    val visitorId: String
        get() {
            //TODO()
            //tealium.
            return _visitorId
        }

    fun track(dispatch: Dispatch) {
        tealium.track(dispatch)
    }
}
