package com.tealium.core.internal.modules

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkConstructor
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class DeviceDataProviderTests {

    @RelaxedMockK
    private lateinit var context: Context

    @RelaxedMockK
    private lateinit var displayManager: DisplayManager

    @RelaxedMockK
    private lateinit var uiModeManager: UiModeManager

    @RelaxedMockK
    private lateinit var defaultDisplay: Display

    private lateinit var deviceDataProvider: DeviceDataProvider

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }


    @Test
    fun device_returnsModel_When_StartsWith_Manufacturer() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Google")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Google Pixel 7")

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("Google Pixel 7", deviceDataProvider.device)
    }

    @Test
    fun device_returns_ManufacturerAndModel_When_DoesNot_StartsWith_Manufacturer() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Google")
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Pixel 7")

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("Google Pixel 7", deviceDataProvider.device)
    }

    @Test
    fun deviceModel_returnsDeviceModel() {
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", "Pixel 7")

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("Pixel 7", deviceDataProvider.deviceModel)
    }

    @Test
    fun deviceManufacturer_returnsDeviceManufacturer() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Google")

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("Google", deviceDataProvider.deviceManufacturer)
    }

    @Test
    fun deviceArchitecture_returns32bit_When_No_Supported_64bit_ABIs() {
        ReflectionHelpers.setStaticField(
            Build::class.java,
            "SUPPORTED_64_BIT_ABIS",
            arrayOf<String>()
        )

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("32bit", deviceDataProvider.deviceArchitecture)
    }

    @Test
    fun deviceArchitecture_returns64bit() {
        ReflectionHelpers.setStaticField(
            Build::class.java,
            "SUPPORTED_64_BIT_ABIS",
            arrayOf<String>("64bit")
        )

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("64bit", deviceDataProvider.deviceArchitecture)
    }

    @Test
    fun deviceCpuType_returnsDeviceCpuType() {
        System.setProperty("os.arch", "aarch64")

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("aarch64", deviceDataProvider.deviceCpuType)
    }

    @Test
    fun deviceResolution_returnsDeviceResolution() {
        val displayMetricsSlot = slot<DisplayMetrics>()

        every {
            defaultDisplay.getRealMetrics(capture(displayMetricsSlot))
        } answers {
            displayMetricsSlot.captured.widthPixels = 1000
            displayMetricsSlot.captured.heightPixels = 2000
        }

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("1000x2000", deviceDataProvider.deviceResolution)
    }

    @Test
    fun deviceLogicalResolution_returnsDeviceLogicalResolution() {
        val displayMetricsSlot = slot<DisplayMetrics>()

        every {
            defaultDisplay.getRealMetrics(capture(displayMetricsSlot))
        } answers {
            displayMetricsSlot.captured.widthPixels = 1000
            displayMetricsSlot.captured.heightPixels = 2000
            displayMetricsSlot.captured.density = 10F
        }

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("100x200", deviceDataProvider.deviceLogicalResolution)
    }

    @Test
    fun deviceRuntime_returnsDeviceRuntime() {
        System.setProperty("java.vm.version", "1.2.3")

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("1.2.3", deviceDataProvider.deviceRuntime)
    }

    @Test
    fun deviceOrigin_returnsTvOrigin_ForTv() {
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_TELEVISION

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("tv", deviceDataProvider.deviceOrigin)
    }

    @Test
    fun deviceOrigin_returnsMobileOrigin_When_Not_TV() {
        every { uiModeManager.currentModeType } returns Configuration.UI_MODE_TYPE_NORMAL

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("mobile", deviceDataProvider.deviceOrigin)
    }

    @Test
    fun devicePlatform_returnsAndroid() {
        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)
        assertEquals("android", deviceDataProvider.devicePlatform)
    }

    @Test
    fun deviceOsName_returnsAndroid() {
        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)
        assertEquals("Android", deviceDataProvider.deviceOsName)
    }

    @Test
    fun deviceOsBuild_returnsDeviceOsBuild() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "INCREMENTAL", "14")

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)
        assertEquals("14", deviceDataProvider.deviceOsBuild)
    }

    @Test
    fun deviceOsVersion_returnsDeviceOsVersion() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "RELEASE", "12345678")

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("12345678", deviceDataProvider.deviceOsVersion)
    }

    @Test
    fun deviceAvailableSystemStorage_returnsDeviceAvailableSystemStorage() {
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } returns 100L
        every { anyConstructed<StatFs>().blockSizeLong } returns 100L

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals(10000, deviceDataProvider.deviceAvailableSystemStorage)
    }

    @Test
    fun deviceAvailableExternalStorage_returnsDeviceAvailableExternalStorage() {
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBlocksLong } returns 100L
        every { anyConstructed<StatFs>().blockSizeLong } returns 100L

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals(10000, deviceDataProvider.deviceAvailableExternalStorage)
    }

    @Test
    fun deviceOrientation_returnsDeviceOrientation() {
        every { displayManager.getDisplay(Display.DEFAULT_DISPLAY) } returns defaultDisplay
        every { defaultDisplay.rotation } returns Surface.ROTATION_0

        deviceDataProvider =
            DeviceDataProviderImpl(context, displayManager, uiModeManager, defaultDisplay)

        assertEquals("Portrait", deviceDataProvider.deviceOrientation)

        every { defaultDisplay.rotation } returns Surface.ROTATION_90

        assertEquals("Landscape Right", deviceDataProvider.deviceOrientation)

        every { defaultDisplay.rotation } returns Surface.ROTATION_180

        assertEquals("Portrait Upside Down", deviceDataProvider.deviceOrientation)

        every { defaultDisplay.rotation } returns Surface.ROTATION_270

        assertEquals("Landscape Left", deviceDataProvider.deviceOrientation)
    }
}