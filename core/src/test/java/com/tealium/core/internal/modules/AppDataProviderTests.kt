package com.tealium.core.internal.modules

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Debug
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.tracking.Dispatch
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

class AppDataProviderTests {

    @RelaxedMockK
    private lateinit var context: Context

    @RelaxedMockK
    private lateinit var activityManager: ActivityManager

    @RelaxedMockK
    private lateinit var applicationInfo: ApplicationInfo

    @RelaxedMockK
    private lateinit var mockPackageInfo: PackageInfo

    @RelaxedMockK
    private lateinit var packageManager: PackageManager

    @RelaxedMockK
    private lateinit var dataStore: DataStore

    private lateinit var appDataProvider: AppDataProvider

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { context.applicationContext } returns context
        every { context.packageManager } returns packageManager
        every { context.applicationInfo } returns applicationInfo
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { context.packageName } returns "com.tealium.com"
        every {
            context.packageManager.getPackageInfo(
                any<String>(),
                any<Int>()
            )
        } returns mockPackageInfo
    }

    @Test
    fun provider_returnsDataStoreUuid() {
        every { dataStore.getString(Dispatch.Keys.APP_UUID) } returns "123456789"

        appDataProvider = AppDataProviderImpl(context, dataStore)

        assertEquals("123456789", appDataProvider.appUuid)
    }

    @Test
    fun provider_createsNewUuid() {
        every { dataStore.getString(Dispatch.Keys.APP_UUID) } returns null

        mockkStatic(UUID::class)
        every { UUID.randomUUID().toString() } returns "test_id"

        appDataProvider = AppDataProviderImpl(context, dataStore)

        assertEquals("test_id", appDataProvider.appUuid)
    }

    @Test
    fun provider_fetchesAppRdns() {
        every { context.packageName } returns "com.tealium.test"

        appDataProvider = AppDataProviderImpl(context, dataStore)

        assertEquals("com.tealium.test", appDataProvider.appRdns)
    }

    @Test
    fun provider_fetchesAppName() {
        every { context.packageManager.getApplicationLabel(any()).toString() } returns "mobile"

        appDataProvider = AppDataProviderImpl(context, dataStore)

        assertEquals("mobile", appDataProvider.appName)
    }

    @Test
    fun provider_fetchesAppBuild() {
        mockPackageInfo.versionCode = 1

        appDataProvider = AppDataProviderImpl(context, dataStore)

        assertEquals("1", appDataProvider.appBuild)
    }

    @Test
    fun provider_fetchesAppVersion() {
        mockPackageInfo.versionName = "1.0.0"

        appDataProvider = AppDataProviderImpl(context, dataStore)

        assertEquals("1.0.0", appDataProvider.appVersion)
    }

    @Test
    fun provider_fetchAppMemoryUsage() {
        val memoryInfo = mockk<Debug.MemoryInfo>()
        every { memoryInfo.totalPss } returns 2048
        every { activityManager.getProcessMemoryInfo(any()) } returns arrayOf(memoryInfo)

        appDataProvider = AppDataProviderImpl(context, dataStore)

        assertEquals(2, appDataProvider.appMemoryUsage)
    }

}