package com.tealium.core.internal.modules

import android.content.Context
import com.tealium.core.BuildConfig
import com.tealium.core.api.Modules
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.settings.DeviceDataSettingsBuilder
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext

/**
 * Collects data related to the user's device.
 */
class DeviceDataModule(
    private val context: Context,
    private val deviceDataProvider: DeviceDataProvider = DeviceDataProviderImpl(context)
) : Collector {

    private val baseData = DataObject.create {
        put(Dispatch.Keys.DEVICE, deviceDataProvider.device)
        put(Dispatch.Keys.DEVICE_MODEL, deviceDataProvider.deviceModel)
        put(Dispatch.Keys.DEVICE_MANUFACTURER, deviceDataProvider.deviceManufacturer)
        put(Dispatch.Keys.DEVICE_ARCHITECTURE, deviceDataProvider.deviceArchitecture)
        put(Dispatch.Keys.DEVICE_CPU_TYPE, deviceDataProvider.deviceCpuType)
        put(Dispatch.Keys.DEVICE_RESOLUTION, deviceDataProvider.deviceResolution)
        put(Dispatch.Keys.DEVICE_LOGICAL_RESOLUTION, deviceDataProvider.deviceLogicalResolution)
        put(Dispatch.Keys.DEVICE_RUNTIME, deviceDataProvider.deviceRuntime)
        put(Dispatch.Keys.DEVICE_ORIGIN, deviceDataProvider.deviceOrigin)
        put(Dispatch.Keys.DEVICE_PLATFORM, deviceDataProvider.devicePlatform)
        put(Dispatch.Keys.DEVICE_OS_NAME, deviceDataProvider.deviceOsName)
        put(Dispatch.Keys.DEVICE_OS_BUILD, deviceDataProvider.deviceOsBuild)
        put(Dispatch.Keys.DEVICE_OS_VERSION, deviceDataProvider.deviceOsVersion)
    }

    override fun collect(dispatchContext: DispatchContext): DataObject {
        return baseData.copy {
            put(
                Dispatch.Keys.DEVICE_AVAILABLE_SYSTEM_STORAGE,
                deviceDataProvider.deviceAvailableSystemStorage
            )
            put(
                Dispatch.Keys.DEVICE_AVAILABLE_EXTERNAL_STORAGE,
                deviceDataProvider.deviceAvailableExternalStorage
            )
            put(Dispatch.Keys.DEVICE_ORIENTATION, deviceDataProvider.deviceOrientation)
            put(Dispatch.Keys.DEVICE_LANGUAGE, deviceDataProvider.deviceLanguage)
            put(Dispatch.Keys.DEVICE_BATTERY_PERCENT, deviceDataProvider.deviceBatteryPercent)
            put(Dispatch.Keys.DEVICE_ISCHARGING, deviceDataProvider.deviceIsCharging)
        }
    }

    override val id: String = Modules.Types.DEVICE_DATA
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    class Factory(
        settings: DataObject? = null
    ) : ModuleFactory {

        private val enforcedSettings: List<DataObject> =
            settings?.let { listOf(it) } ?: emptyList()

        constructor(settingsBuilder: DeviceDataSettingsBuilder) : this(settingsBuilder.build())

        override fun getEnforcedSettings(): List<DataObject> = enforcedSettings

        override val moduleType: String = Modules.Types.DEVICE_DATA

        override fun create(moduleId: String, context: TealiumContext, configuration: DataObject): Module? {
            return DeviceDataModule(context.context)
        }
    }
}