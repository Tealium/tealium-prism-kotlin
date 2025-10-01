package com.tealium.prism.core.internal.modules

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The [DeviceDataProvider] interface defines data information gathered about a user's device.
 */
interface DeviceDataProvider {
    val device: String
    val deviceModel: String
    val deviceManufacturer: String
    val deviceArchitecture: String
    val deviceCpuType: String
    val deviceResolution: String
    val deviceLogicalResolution: String
    val deviceRuntime: String
    val deviceOrigin: String
    val devicePlatform: String
    val deviceOsName: String
    val deviceOsBuild: String
    val deviceOsVersion: String
    val deviceAvailableSystemStorage: Long
    val deviceAvailableExternalStorage: Long
    val deviceOrientation: String
    val deviceLanguage: String
    val deviceBatteryPercent: Int
    val deviceIsCharging: Boolean
}

class DeviceDataProviderImpl(
    private val context: Context,
    private val displayManager: DisplayManager,
    private val uiModeManager: UiModeManager,
    private val defaultDisplay: Display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
) : DeviceDataProvider {
    constructor(context: Context) : this(
        context,
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager,
        context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    )

    private val intent = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    private val batteryStatus
        get() = context.registerReceiver(null, intent)

    override val device: String = if (Build.MODEL.startsWith(Build.MANUFACTURER)) Build.MODEL
        ?: "" else "${Build.MANUFACTURER} ${Build.MODEL}"
    override val deviceModel: String = Build.MODEL
    override val deviceManufacturer: String = Build.MANUFACTURER
    override val deviceArchitecture: String =
        if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "64bit" else "32bit"
    override val deviceCpuType: String = System.getProperty("os.arch") ?: "unknown"
    override val deviceResolution: String =
        with(DisplayMetrics()) {
            // includes system decorations: nav bar and status bar
            defaultDisplay.getRealMetrics(this)
            "${widthPixels}x${heightPixels}"
        }
    override val deviceLogicalResolution: String =
        with(DisplayMetrics()) {
            defaultDisplay.getRealMetrics(this)
            val x = ceil(widthPixels / density).toInt()
            val y = ceil(heightPixels / density).toInt()
            "${x}x${y}"
        }
    override val deviceRuntime: String = System.getProperty("java.vm.version") ?: "unknown"
    override val deviceOrigin: String =
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) "tv" else "mobile"
    override val devicePlatform: String = "android"
    override val deviceOsName: String = "Android"
    override val deviceOsBuild: String = Build.VERSION.INCREMENTAL ?: ""
    override val deviceOsVersion: String = Build.VERSION.RELEASE ?: ""
    override val deviceAvailableSystemStorage: Long
        get() {
            return StatFs(Environment.getRootDirectory().path).let {
                (it.availableBlocksLong * it.blockSizeLong)
            }
        }
    override val deviceAvailableExternalStorage: Long
        get() {
            return StatFs(Environment.getExternalStorageDirectory().path).let { external ->
                (external.availableBlocksLong * external.blockSizeLong)
            }
        }
    override val deviceOrientation: String
        get() {
            return when (defaultDisplay.rotation) {
                Surface.ROTATION_90 -> "Landscape Right"
                Surface.ROTATION_180 -> "Portrait Upside Down"
                Surface.ROTATION_270 -> "Landscape Left"
                else -> "Portrait"
            }
        }
    override val deviceLanguage: String
        get() = Locale.getDefault().toLanguageTag()
    override val deviceBatteryPercent: Int
        get() {
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

            return ((level.toFloat() / scale.toFloat()) * 100).roundToInt()
        }
    override val deviceIsCharging: Boolean
        get() {
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            return status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL
        }
}