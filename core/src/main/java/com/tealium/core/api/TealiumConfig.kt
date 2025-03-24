package com.tealium.core.api

import android.app.Application
import com.tealium.core.api.barriers.Barrier
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.LogHandler
import com.tealium.core.api.misc.Environment
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.settings.CoreSettingsBuilder
import com.tealium.core.internal.logger.LoggerImpl
import com.tealium.core.internal.settings.CoreSettingsImpl
import java.io.File

class TealiumConfig @JvmOverloads constructor(
    val application: Application,
    val accountName: String,
    val profileName: String,
    val environment: Environment,
    val modules: List<ModuleFactory>,
    val datasource: String? = null,
    enforcingCoreSettings: ((CoreSettingsBuilder) -> CoreSettingsBuilder)? = null
) {
    val key: String = "${accountName}-${profileName}"

    private val pathName
        get() = "${application.filesDir}${File.separatorChar}tealium${File.separatorChar}${accountName}${File.separatorChar}${profileName}${File.separatorChar}${environment.environment}"
    val tealiumDirectory: File
        get() = File(pathName)

    var useRemoteSettings: Boolean = false

    /**
     * File name for local settings
     */
    var localSdkSettingsFileName: String? = null

    /**
     * Overrides remote URL to use when fetching remote settings
     */
    var sdkSettingsUrl: String? = null

    /**
     * Overrides default LogHandler. Default will log to the Android console.
     */
    var logHandler: LogHandler = LoggerImpl.ConsoleLogHandler

    var barriers: Set<Barrier> = setOf()

    /**
     * Sets a known existing visitor id for use only on first launch.
     */
    var existingVisitorId: String? = null


    val enforcedSdkSettings: DataObject = DataObject.create {
        enforcingCoreSettings?.let { builderBlock ->
            val coreSettings = builderBlock.invoke(CoreSettingsBuilder())
                .build()
            put(CoreSettingsImpl.MODULE_NAME, coreSettings)
        }

        modules.forEach { factory ->
            factory.getEnforcedSettings()?.let { enforcedSettings ->
                put(factory.id, enforcedSettings)
            }
        }
    }

}