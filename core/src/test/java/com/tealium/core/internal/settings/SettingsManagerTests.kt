package com.tealium.core.internal.settings

import android.app.Application
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.internal.persistence.getTimestamp
import com.tealium.core.internal.persistence.getTimestampMilliseconds
import com.tealium.core.internal.pubsub.Subscription
import com.tealium.tests.common.TestModuleFactory
import com.tealium.tests.common.getDefaultConfig
import io.mockk.CapturingSlot
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class SettingsManagerTests {

    lateinit var app: Application

    @RelaxedMockK
    lateinit var mockNetworkHelper: NetworkHelper

    @RelaxedMockK
    lateinit var mockSettingsDataStore: DataStore

    @RelaxedMockK
    lateinit var mockLogger: Logger

    private lateinit var config: TealiumConfig
    private lateinit var settingsManager: SettingsManager
    private lateinit var settingsSubject: StateSubject<SdkSettings>

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        app = RuntimeEnvironment.getApplication()

        config = getDefaultConfig(app)
        // start with defaults
        settingsSubject = Observables.stateSubject(SdkSettings())
    }

    @After
    fun tearDown() {
        unmockkObject(SettingsManager)
    }

    @Test
    fun loadDefaultSetOfSdkSettings() {
        mockAssetResponse(null)
        createSettingsManager()

        val settings = settingsManager.loadSettings()

        assertTrue(settings.moduleSettings.isEmpty())
    }

    @Test
    fun loadSettings_DoesNot_LoadFromLocal_WhenUseRemoteSettingsIsFalse() {
        config.useRemoteSettings = false
        mockAssetResponse()
        createSettingsManager()

        settingsManager.loadSettings()

        verify(exactly = 0) { mockNetworkHelper.getTealiumBundle(any<String>(), any(), any()) }
        verify(exactly = 0) { mockNetworkHelper.getTealiumBundle(any<URL>(), any(), any()) }
        verify { SettingsManager.loadFromAsset(config) }
    }

    @Test
    fun refreshRemote_FetchesFromRemote_And_Local_WhenUseRemoteSettingsIsTrue() {
        config.useRemoteSettings = true
        config.sdkSettingsUrl = "localhost"
        mockAssetResponse()
        createSettingsManager()

        settingsManager.refreshRemote()

        verify { mockNetworkHelper.getTealiumBundle(any<String>(), any(), any()) }
    }

    @Test
    fun refreshRemote_EmitsUpdatedSdkSettings_When_Available() {
        config.useRemoteSettings = true
        config.sdkSettingsUrl = "localhost"
        val onComplete = mockk<Observer<SdkSettings>>(relaxed = true)
        mockAssetResponse(null)
        mockRemoteResponse(
            SdkSettings(mapOf("test" to TealiumBundle.EMPTY_BUNDLE))
                .asTealiumValue()
                .getBundle()
        )
        createSettingsManager()

        settingsManager.onSdkSettingsUpdated
            .subscribe(onComplete)
        settingsManager.refreshRemote()

        verify(exactly = 1) {
            onComplete.onNext(match {
                it.moduleSettings.containsKey("test")
            })
        }
    }

    @Test
    fun refreshRemote_FetchesNewSettings_When_TimedOut() {
        config.useRemoteSettings = true
        config.sdkSettingsUrl = "localhost"
        val onComplete = mockk<Observer<SdkSettings>>(relaxed = true)
        val timingProvider = mockk<() -> Long>()
        every { timingProvider.invoke() } returnsMany listOf(
            getTimestampMilliseconds(),
            getTimestampMilliseconds() + 1001
        )
        mockAssetResponse(null)
        val completionCapture = slot<(TealiumBundle?) -> Unit>()
        mockRemoteResponse(
            SdkSettings(mapOf("test" to TealiumBundle.EMPTY_BUNDLE))
                .asTealiumValue()
                .getBundle(),
            completionCapture
        )
        createSettingsManager(timingProvider = timingProvider, refreshTimeout = 1000)

        settingsManager.onSdkSettingsUpdated
            .subscribe(onComplete)
        settingsManager.refreshRemote()

        verify(exactly = 1) {
            onComplete.onNext(match {
                it.moduleSettings.containsKey("test")
            })
        }

        completionCapture.clear()
        mockRemoteResponse(
            SdkSettings(mapOf("test2" to TealiumBundle.EMPTY_BUNDLE))
                .asTealiumValue()
                .getBundle(),
            completionCapture
        )

        settingsManager.refreshRemote()
        verify(exactly = 1) {
            onComplete.onNext(match {
                it.moduleSettings.containsKey("test2")
            })
        }
    }

    @Test
    fun mergeSettings_Returns_Local_When_OnlyLocalAvailable() {
        val localSettings =
            SdkSettings(moduleSettings = mapOf("core" to TealiumBundle.create {
                put("data_source", "test")
            })).asTealiumValue().getBundle()

        val mergedSettings =
            SettingsManager.mergeSettings(config = config, localSettings = localSettings)

        assertTrue(mergedSettings.moduleSettings.size == 1)
        assertTrue(mergedSettings.moduleSettings.containsKey("core"))
        assertTrue(mergedSettings.moduleSettings["core"]?.getString("data_source") == "test")
    }

    @Test
    fun mergeSettings_Prefers_Remote_Over_Local_Settings() {
        val localSettings =
            SdkSettings(
                moduleSettings = mapOf(
                    "core" to TealiumBundle.create {
                        put(CoreSettingsImpl.KEY_DATA_SOURCE, "testSource")
                    }
                )
            ).asTealiumValue().getBundle()
        val remoteSettings =
            SdkSettings(
                moduleSettings = mapOf(
                    "core" to TealiumBundle.create {
                        put(CoreSettingsImpl.KEY_DATA_SOURCE, "remoteSource")
                    }
                )
            ).asTealiumValue().getBundle()

        val mergedSettings = SettingsManager.mergeSettings(
            config,
            remoteSettings = remoteSettings,
            localSettings = localSettings
        )

        assertTrue(mergedSettings.moduleSettings.size == 1)
        assertTrue(mergedSettings.moduleSettings.containsKey("core"))
        assertTrue(mergedSettings.moduleSettings["core"]?.getString(CoreSettingsImpl.KEY_DATA_SOURCE) == "remoteSource")
    }

    @Test
    fun mergeSettings_Prefers_Programmatic_Settings_Over_Remote_Or_Local_Settings() {
        val localSettings =
            SdkSettings(
                moduleSettings = mapOf(
                    "core" to TealiumBundle.create {
                        put(CoreSettingsImpl.KEY_DATA_SOURCE, "testSource")
                    }
                )
            ).asTealiumValue().getBundle()
        val remoteSettings =
            SdkSettings(
                moduleSettings = mapOf(
                    "core" to TealiumBundle.create {
                        put(CoreSettingsImpl.KEY_DATA_SOURCE, "remoteSource")
                        put(CoreSettingsImpl.KEY_LOG_LEVEL, "TRACE")
                    }
                )
            ).asTealiumValue().getBundle()

        config = getDefaultConfig(app) {
            it.setDataSource("programmaticSource")
        }

        val mergedSettings = SettingsManager.mergeSettings(
            config,
            remoteSettings = remoteSettings,
            localSettings = localSettings
        )

        assertTrue(mergedSettings.moduleSettings.size == 1)
        assertTrue(mergedSettings.moduleSettings.containsKey("core"))
        assertTrue(mergedSettings.moduleSettings["core"]?.getString(CoreSettingsImpl.KEY_DATA_SOURCE) == "programmaticSource")
        assertTrue(mergedSettings.moduleSettings["core"]?.getString(CoreSettingsImpl.KEY_LOG_LEVEL) == "TRACE")
    }

    @Test
    fun mergeSettings_MergesFirstLevelOnly() {
        val localSettings =
            SdkSettings(moduleSettings = mapOf("test" to TealiumBundle.create {
                put("key1", "value1")
                put("key2", "value2")
                put("key3", "value3")
            })).asTealiumValue().getBundle()
        val remoteSettings =
            SdkSettings(moduleSettings = mapOf("test" to TealiumBundle.create {
                put("key2", "value22")
                put("key4", "value4")
            })).asTealiumValue().getBundle()

        val property1 = TealiumBundle.create {
            put("key2", "value222")
            put("key4", "value444")
            put("key5", "value5")
        }
        config = getDefaultConfig(
            app, modules = listOf(
                TestModuleFactory("test", TealiumBundle.create {
                    put("property1", property1)
                }) { _, _ -> null }
            ))

        val mergedSettings = SettingsManager.mergeSettings(
            config,
            remoteSettings = remoteSettings,
            localSettings = localSettings
        )

        assertTrue(mergedSettings.moduleSettings.containsKey("test"))

        val prop1 = mergedSettings.moduleSettings["test"]?.getBundle("property1")

        assertEquals(property1, prop1)
        assertNull(prop1?.getString("key1"))
        assertNull(prop1?.getString("key3"))
    }

    @Test
    fun loadFromAsset_ReturnsBundle_When_ValidFile() {
        config.localSdkSettingsFileName = "validSettings.json"

        val result = SettingsManager.loadFromAsset(config)

        assertNotNull(result)
        assertEquals(
            TealiumValue.int(6),
            result?.get("core")?.getBundle()?.get("batch_size")
        )
    }

    @Test
    fun loadFromAsset_ReturnsNull_When_InvalidFile() {
        config.localSdkSettingsFileName = "invalidSettings.json"

        val result = SettingsManager.loadFromAsset(config)

        assertNull(result)
    }

    @Test
    fun loadFromAsset_ReadsLocalAsset_When_ValidFileNameProvided() {
        every { mockSettingsDataStore.get(SettingsManager.KEY_SDK_SETTINGS) } returns null
        config.useRemoteSettings = true

        SettingsManager.loadFromCache(config, mockSettingsDataStore)

        verify {
            mockSettingsDataStore.get(SettingsManager.KEY_SDK_SETTINGS)
        }
    }

    @Test
    fun loadFromCache_ReadsFromDataStore_When_RemoteSettingsEnabled() {
        every { mockSettingsDataStore.get(SettingsManager.KEY_SDK_SETTINGS) } returns null
        config.useRemoteSettings = true

        SettingsManager.loadFromCache(config, mockSettingsDataStore)

        verify {
            mockSettingsDataStore.get(SettingsManager.KEY_SDK_SETTINGS)
        }
    }

    @Test
    fun loadFromCache_DoesNot_ReadFromDataStore_When_RemoteSettingsDisabled() {
        every { mockSettingsDataStore.get(SettingsManager.KEY_SDK_SETTINGS) } returns null
        config.useRemoteSettings = false

        SettingsManager.loadFromCache(config, mockSettingsDataStore)

        verify(inverse = true) {
            mockSettingsDataStore.get(SettingsManager.KEY_SDK_SETTINGS)
        }
    }

    @Test
    fun isTimedOut_ReturnsTrue_When_TimeoutIsLessThanElapsed() {
        assertTrue(
            SettingsManager.isTimedOut(
                10, 0, 5
            )
        )
        assertTrue(
            SettingsManager.isTimedOut(
                10, 1, 8
            )
        )
    }

    @Test
    fun isTimedOut_ReturnsFalse_When_TimeoutIsGreaterThanElapsed() {
        assertFalse(
            SettingsManager.isTimedOut(
                10, 0, 11
            )
        )
        assertFalse(
            SettingsManager.isTimedOut(
                10, 1, 9
            )
        )
    }

    private fun createSettingsManager(
        config: TealiumConfig = this.config,
        mockNetworkHelper: NetworkHelper = this.mockNetworkHelper,
        mockSettingsDataStore: DataStore = this.mockSettingsDataStore,
        mockLogger: Logger = this.mockLogger,
        settingsSubject: StateSubject<SdkSettings> = this.settingsSubject,
        refreshTimeout: Long = SettingsManager.REFRESH_INTERVAL_MILLIS,
        lastRefreshTime: Long = 0,
        timingProvider: () -> Long = ::getTimestamp,
    ): SettingsManager {
        settingsManager = SettingsManager(
            config,
            mockNetworkHelper,
            mockSettingsDataStore,
            mockLogger,
            settingsSubject,
            refreshTimeout = refreshTimeout,
            lastRefreshTime = lastRefreshTime,
            timingProvider = timingProvider
        )
        return settingsManager
    }

    /**
     * Mocks the response from [SettingsManager.loadFromAsset]
     *
     * If omitted, then [bundle] will return the bundle generated by the default SdkSettings.
     */
    private fun mockAssetResponse(
        bundle: TealiumBundle? = SdkSettings().asTealiumValue().getBundle()!!
    ) {
        mockkObject(SettingsManager)
        every { SettingsManager.loadFromAsset(any()) } returns bundle
    }

    /**
     * Mocks the response from a remote source
     *
     * If omitted, then [bundle] will return the bundle generated by the default SdkSettings.
     */
    private fun mockRemoteResponse(
        bundle: TealiumBundle? = SdkSettings().asTealiumValue().getBundle()!!,
        completionCapture: CapturingSlot<(TealiumBundle?) -> Unit> = slot<(TealiumBundle?) -> Unit>()
    ) {
        every {
            mockNetworkHelper.getTealiumBundle(
                any<String>(),
                any(),
                capture(completionCapture)
            )
        } answers {
            completionCapture.captured.invoke(bundle)
            Subscription()
        }
    }
}
