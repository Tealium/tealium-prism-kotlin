package com.tealium.core

import android.app.Application
import com.tealium.core.api.barriers.Barrier
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.listeners.Listener
import com.tealium.core.api.logger.LogHandler
import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.api.settings.ModuleSettingsBuilder
import com.tealium.core.api.transformations.Transformer
import com.tealium.core.internal.LoggerImpl
import java.io.File

class TealiumConfig @JvmOverloads constructor(
    val application: Application,
    val accountName: String,
    val profileName: String,
    val environment: Environment,
    val modules: List<ModuleFactory>,
    val events: List<Listener> = emptyList(),
    val datasource: String? = null
) {

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

    var transformers: Set<Transformer> = setOf()

    var barriers: Set<Barrier> = setOf()

    /**
     * Sets a known existing visitor id for use only on first launch.
     */
    var existingVisitorId: String? = null

    /**
     * Holds settings configurations for core and integrated modules
     */
    internal val modulesSettings: MutableMap<String, ModuleSettings> = mutableMapOf()

    fun addModuleSettings(vararg settings: ModuleSettingsBuilder) {
        settings.forEach {
            modulesSettings[it.moduleName] = it.build()
        }
    }
}