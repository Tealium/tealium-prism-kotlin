package com.tealium.core.internal

import android.util.Log
import com.tealium.core.BuildConfig
import com.tealium.core.LogLevel
import com.tealium.core.api.CoreSettings
import com.tealium.core.api.Logging
import com.tealium.core.api.ModuleSettings
import com.tealium.core.api.listeners.SettingsUpdatedListener

class Logger(
    private var logLevel: LogLevel = LogLevel.PROD
) : Logging, SettingsUpdatedListener {

    override fun onSettingsUpdated(
        coreSettings: CoreSettings,
        moduleSettings: Map<String, ModuleSettings>
    ) {
        if (coreSettings.logLevel != logLevel) {
            logLevel = coreSettings.logLevel
        }
    }

    override fun debug(tag: String, msg: String) {
        if (!shouldLog(LogLevel.DEV)) return

        Log.d(BuildConfig.TAG, msg)
    }

    override fun debug(tag: String, msg: () -> String) {
        if (!shouldLog(LogLevel.DEV)) return

        Log.d(BuildConfig.TAG, msg())
    }

    override fun info(tag: String, msg: String) {
        if (!shouldLog(LogLevel.QA)) return

        Log.w(BuildConfig.TAG, msg)
    }

    override fun info(tag: String, msg: () -> String) {
        if (!shouldLog(LogLevel.QA)) return

        Log.w(BuildConfig.TAG, msg())
    }

    override fun error(tag: String, msg: String) {
        if (!shouldLog(LogLevel.PROD)) return

        Log.e(BuildConfig.TAG, msg)
    }

    override fun error(tag: String, msg: () -> String) {
        if (!shouldLog(LogLevel.PROD)) return

        Log.e(BuildConfig.TAG, msg())
    }

    private fun shouldLog(logLevel: LogLevel): Boolean {
        return logLevel.level >= this.logLevel.level
    }
}