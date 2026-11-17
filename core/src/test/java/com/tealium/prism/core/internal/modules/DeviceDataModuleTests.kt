package com.tealium.prism.core.internal.modules

import android.content.Context
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDataModuleTests {

    private val logger = mockk<Logger>(relaxed = true)

    @Test
    fun collect_Collects_All_DeviceDataProvider_Data_In_Correct_Keys() {
        val mockContext = mockk<Context>()
        val mockDeviceDataProvider = mockk<DeviceDataProvider>()
        val dispatchContext =
            DispatchContext(DispatchContext.Source.application(), DataObject.EMPTY_OBJECT)

        every { mockDeviceDataProvider.device } returns "Google Pixel 7"
        every { mockDeviceDataProvider.deviceModel } returns "Pixel 7"
        every { mockDeviceDataProvider.deviceManufacturer } returns "Google"
        every { mockDeviceDataProvider.deviceArchitecture } returns "64bit"
        every { mockDeviceDataProvider.deviceCpuType } returns "aarch64"
        every { mockDeviceDataProvider.deviceResolution } returns "100x200"
        every { mockDeviceDataProvider.deviceLogicalResolution } returns "300x400"
        every { mockDeviceDataProvider.deviceRuntime } returns "3.2.1"
        every { mockDeviceDataProvider.deviceOrigin } returns "mobile"
        every { mockDeviceDataProvider.devicePlatform } returns "android"
        every { mockDeviceDataProvider.deviceOsName } returns "Android"
        every { mockDeviceDataProvider.deviceOsBuild } returns "1.2.3"
        every { mockDeviceDataProvider.deviceOsVersion } returns "14"
        every { mockDeviceDataProvider.deviceAvailableSystemStorage } returns 2000L
        every { mockDeviceDataProvider.deviceAvailableExternalStorage } returns 1000L
        every { mockDeviceDataProvider.deviceLanguage } returns "en-US"
        every { mockDeviceDataProvider.deviceOrientation } returns "Landscape Right"
        every { mockDeviceDataProvider.deviceBatteryPercent } returns 85
        every { mockDeviceDataProvider.deviceIsCharging } returns false

        val deviceDataModule = DeviceDataModule(mockContext, logger, mockDeviceDataProvider)
        val collectedData = deviceDataModule.collect(dispatchContext)

        assertEquals("Google Pixel 7", collectedData.getString(Dispatch.Keys.DEVICE))
        assertEquals("Pixel 7", collectedData.getString(Dispatch.Keys.DEVICE_MODEL))
        assertEquals("Google", collectedData.getString(Dispatch.Keys.DEVICE_MANUFACTURER))
        assertEquals("64bit", collectedData.getString(Dispatch.Keys.DEVICE_ARCHITECTURE))
        assertEquals("aarch64", collectedData.getString(Dispatch.Keys.DEVICE_CPU_TYPE))
        assertEquals("100x200", collectedData.getString(Dispatch.Keys.DEVICE_RESOLUTION))
        assertEquals("300x400", collectedData.getString(Dispatch.Keys.DEVICE_LOGICAL_RESOLUTION))
        assertEquals("3.2.1", collectedData.getString(Dispatch.Keys.DEVICE_RUNTIME))
        assertEquals("mobile", collectedData.getString(Dispatch.Keys.DEVICE_ORIGIN))
        assertEquals("android", collectedData.getString(Dispatch.Keys.DEVICE_PLATFORM))
        assertEquals("Android", collectedData.getString(Dispatch.Keys.DEVICE_OS_NAME))
        assertEquals("1.2.3", collectedData.getString(Dispatch.Keys.DEVICE_OS_BUILD))
        assertEquals("14", collectedData.getString(Dispatch.Keys.DEVICE_OS_VERSION))
        assertEquals(2000L, collectedData.getLong(Dispatch.Keys.DEVICE_AVAILABLE_SYSTEM_STORAGE))
        assertEquals(1000L, collectedData.getLong(Dispatch.Keys.DEVICE_AVAILABLE_EXTERNAL_STORAGE))
        assertEquals("en-US", collectedData.getString(Dispatch.Keys.DEVICE_LANGUAGE))
        assertEquals("Landscape Right", collectedData.getString(Dispatch.Keys.DEVICE_ORIENTATION))
        assertEquals(85, collectedData.getInt(Dispatch.Keys.DEVICE_BATTERY_PERCENT))
        assertEquals(false, collectedData.getBoolean(Dispatch.Keys.DEVICE_ISCHARGING))
        verify { logger wasNot Called }
    }

    @Test
    fun collect_OmitsStorageKeys_And_CollectsOtherData_When_StorageReadsThrow() {
        val mockDeviceDataProvider = relaxedProvider()
        every { mockDeviceDataProvider.deviceAvailableSystemStorage } throws invalidPath("/system")
        every {
            mockDeviceDataProvider.deviceAvailableExternalStorage
        } throws invalidPath("/sdcard")

        val collectedData = DeviceDataModule(mockk<Context>(), logger, mockDeviceDataProvider)
            .collect(dispatchContext())

        assertNull(collectedData.get(Dispatch.Keys.DEVICE_AVAILABLE_SYSTEM_STORAGE))
        assertNull(collectedData.get(Dispatch.Keys.DEVICE_AVAILABLE_EXTERNAL_STORAGE))
        assertEquals("Pixel 7", collectedData.getString(Dispatch.Keys.DEVICE_MODEL))
        assertEquals("en-US", collectedData.getString(Dispatch.Keys.DEVICE_LANGUAGE))
        assertEquals(85, collectedData.getInt(Dispatch.Keys.DEVICE_BATTERY_PERCENT))
    }

    @Test
    fun collect_OmitsOnlyFailingStorageKey_When_OneStorageReadThrows() {
        val mockDeviceDataProvider = relaxedProvider()
        every { mockDeviceDataProvider.deviceAvailableSystemStorage } returns 2000L
        every {
            mockDeviceDataProvider.deviceAvailableExternalStorage
        } throws invalidPath("/sdcard")

        val collectedData = DeviceDataModule(mockk<Context>(), logger, mockDeviceDataProvider)
            .collect(dispatchContext())

        assertEquals(2000L, collectedData.getLong(Dispatch.Keys.DEVICE_AVAILABLE_SYSTEM_STORAGE))
        assertNull(collectedData.get(Dispatch.Keys.DEVICE_AVAILABLE_EXTERNAL_STORAGE))
    }

    @Test
    fun collect_LogsWarningOnce_When_StorageReadsThrowRepeatedly() {
        val mockDeviceDataProvider = relaxedProvider()
        every { mockDeviceDataProvider.deviceAvailableSystemStorage } throws invalidPath("/system")
        every {
            mockDeviceDataProvider.deviceAvailableExternalStorage
        } throws invalidPath("/sdcard")
        val module = DeviceDataModule(mockk<Context>(), logger, mockDeviceDataProvider)

        repeat(3) { module.collect(dispatchContext()) }

        verify(exactly = 1) { logger.warn(Modules.Types.DEVICE_DATA, any<String>()) }
    }

    @Test
    fun collect_IncludesStorageKey_When_ReadSucceedsAfterFailure() {
        val mockDeviceDataProvider = relaxedProvider()
        every {
            mockDeviceDataProvider.deviceAvailableSystemStorage
        } throws invalidPath("/system") andThen 2000L
        val module = DeviceDataModule(mockk<Context>(), logger, mockDeviceDataProvider)

        val first = module.collect(dispatchContext())
        val second = module.collect(dispatchContext())

        assertNull(first.get(Dispatch.Keys.DEVICE_AVAILABLE_SYSTEM_STORAGE))
        assertEquals(2000L, second.getLong(Dispatch.Keys.DEVICE_AVAILABLE_SYSTEM_STORAGE))
        verify(exactly = 1) { logger.warn(Modules.Types.DEVICE_DATA, any<String>()) }
    }

    @Test
    fun collect_WarningNamesKeyAndCause_When_StorageReadThrows() {
        val mockDeviceDataProvider = relaxedProvider()
        every { mockDeviceDataProvider.deviceAvailableSystemStorage } throws invalidPath("/system")
        every { mockDeviceDataProvider.deviceAvailableExternalStorage } returns 1000L
        val message = slot<String>()

        DeviceDataModule(mockk<Context>(), logger, mockDeviceDataProvider)
            .collect(dispatchContext())

        verify { logger.warn(Modules.Types.DEVICE_DATA, capture(message)) }
        assertTrue(message.captured.contains(Dispatch.Keys.DEVICE_AVAILABLE_SYSTEM_STORAGE))
        assertTrue(message.captured.contains("Invalid path: /system"))
        assertTrue(message.captured.contains("(statvfs failed: ENOENT"))
    }

    @Test
    fun factory_ModuleType_Matches_Module_Id() {
        assertEquals(Modules.Types.DEVICE_DATA, DeviceDataModule.Factory().moduleType)
    }

    private fun relaxedProvider(): DeviceDataProvider {
        val provider = mockk<DeviceDataProvider>(relaxed = true)
        every { provider.deviceModel } returns "Pixel 7"
        every { provider.deviceLanguage } returns "en-US"
        every { provider.deviceBatteryPercent } returns 85
        return provider
    }

    private fun dispatchContext() =
        DispatchContext(DispatchContext.Source.application(), DataObject.EMPTY_OBJECT)

    private fun invalidPath(path: String) = IllegalArgumentException(
        "Invalid path: $path",
        Exception("statvfs failed: ENOENT (No such file or directory)")
    )
}
