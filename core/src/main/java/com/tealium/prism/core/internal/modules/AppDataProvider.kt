package com.tealium.prism.core.internal.modules

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.pm.PackageInfo
import android.os.Process
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.tracking.Dispatch.Keys.APP_UUID
import java.util.UUID


/**
 * The [AppDataProvider] interface defines data information gathered about the application package.
 */
interface AppDataProvider {

    /**
     * Unique, random identifier persistent through the lifetime of the app installation. Resets
     * if app is uninstalled
     */
    val appUuid: String

    /**
     * Reverse DNS application ID
     */
    val appRdns: String

    /**
     * Application Name
     */
    val appName: String

    /**
     * Application build version
     */
    val appBuild: String

    /**
     * Application version
     */
    val appVersion: String

    /**
     * Memory in use by application process
     */
    val appMemoryUsage: Long
}

class AppDataProviderImpl(
    private val context: Context,
    private val dataStore: DataStore
) : AppDataProvider {

    private val activityManager =
        context.applicationContext.getSystemService(Service.ACTIVITY_SERVICE) as ActivityManager

    override val appUuid: String
        get() {
            return dataStore.getString(APP_UUID)
                ?: UUID.randomUUID().toString().also {
                    dataStore.edit().put(APP_UUID, it, Expiry.FOREVER).commit()
                }
        }
    override val appRdns: String = context.applicationContext.packageName
    override val appName: String =
        context.packageManager.getApplicationLabel(context.applicationInfo).toString()
    override val appBuild: String = getPackageContext().versionCode.toString()
    override val appVersion: String = getPackageContext().versionName?.toString() ?: ""
    override val appMemoryUsage: Long
        get() {
            var memoryUsage = 0L
            try {
                val pids = arrayOf(Process.myPid())
                activityManager.getProcessMemoryInfo(pids.toIntArray()).forEach {
                    memoryUsage += it.totalPss
                }
                memoryUsage = memoryUsage.div(1024)
            } catch (e: Exception) {
                //ignore
            }
            return memoryUsage
        }

    private fun getPackageContext(): PackageInfo {
        return context.packageManager.getPackageInfo(context.packageName, 0)
    }
}