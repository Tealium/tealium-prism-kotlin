package com.tealium.core.internal.settings

import android.app.Application
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.logger.AlternateLogger
import com.tealium.core.api.misc.ActivityManager
import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.misc.TimeFrameUtils.minutes
import com.tealium.core.api.misc.TimeFrameUtils.seconds
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.network.ResourceCache
import com.tealium.core.api.network.ResourceRefresher
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.api.settings.CoreSettingsBuilder
import com.tealium.core.internal.network.mockGetDataItemConvertibleSuccess
import com.tealium.tests.common.getDefaultConfig
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
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

@RunWith(RobolectricTestRunner::class)
class SettingsManagerTests {

    lateinit var app: Application

    @RelaxedMockK
    lateinit var mockNetworkHelper: NetworkHelper

    @RelaxedMockK
    lateinit var mockCache: ResourceCache<DataObject>

    @RelaxedMockK
    lateinit var mockLogger: AlternateLogger

    private lateinit var config: TealiumConfig
    private lateinit var settingsManager: SettingsManager
    private lateinit var settingsSubject: StateSubject<SdkSettings>

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        app = RuntimeEnvironment.getApplication()
        config = getDefaultConfig(app)
        every {
            mockCache.resource
        } returns null
        // start with defaults
        settingsSubject = Observables.stateSubject(SdkSettings())
    }

    @After
    fun tearDown() {
        unmockkObject(SettingsManager)
    }

    @Test
    fun init_Creates_Empty_Settings_When_No_Local_Or_Remote_Or_Programmatic() {
        mockAssetResponse(null)
        createSettingsManager()

        val settings = settingsManager.sdkSettings.value

        assertTrue(settings.moduleSettings.isEmpty())
    }

    @Test
    fun init_Does_Not_Load_From_Cache_When_UseRemoteSettings_Is_False() {
        config.useRemoteSettings = false
        mockAssetResponse()
        createSettingsManager()

        verify { SettingsManager.loadFromAsset(config) }
        verify(inverse = true) {
            mockCache.resource
        }
    }

    @Test
    fun init_Does_Create_From_Cache_When_UseRemoteSettings_Is_True() {
        config.useRemoteSettings = true
        mockAssetResponse()
        createSettingsManager()

        verify {
            SettingsManager.loadFromAsset(config)
            mockCache.resource
        }
    }

    @Test
    fun init_Does_Create_ResourceRefresher_When_RemoteSettings_Enabled_And_Url_Provided() {
        config.useRemoteSettings = false
        config.sdkSettingsUrl = "https://localhost/"
        val refresher = SettingsManager.createResourceRefresher(
            config, mockNetworkHelper, 10.minutes, mockCache, mockLogger
        )

        assertNotNull(refresher)
    }

    @Test
    fun init_Does_Not_Create_ResourceRefresher_When_RemoteSettings_Disabled() {
        config.useRemoteSettings = false
        val refresher = SettingsManager.createResourceRefresher(
            config, mockNetworkHelper, 10.minutes, mockCache, mockLogger
        )

        assertNull(refresher)
    }

    @Test
    fun init_Does_Not_Create_ResourceRefresher_When_Missing_Url() {
        config.sdkSettingsUrl = null
        val refresher = SettingsManager.createResourceRefresher(
            config, mockNetworkHelper, 10.minutes, mockCache, mockLogger
        )

        assertNull(refresher)
    }

    @Test
    fun subscribeToActivityUpdates_Returns_Disposed_When_Url_Not_Provided() {
        config.sdkSettingsUrl = null
        createSettingsManager()
        val activities = Observables.publishSubject<ActivityManager.ApplicationStatus>()

        val disposable = settingsManager.subscribeToActivityUpdates(activities)

        assertTrue(disposable.isDisposed)
        verify {
            mockNetworkHelper wasNot Called
        }
    }

    @Test
    fun subscribeToActivityUpdates_Starts_Emitting_Updated_Remote_Settings() {
        config.sdkSettingsUrl = "https://localhost/"
        config.useRemoteSettings = true
        val observer = mockk<(SdkSettings) -> Unit>(relaxed = true)
        mockRemoteResponse(
            configureCoreSettingsDataObject {
                setDataSource("data_source")
            }
        )
        createSettingsManager()
        val activities = Observables.publishSubject<ActivityManager.ApplicationStatus>()

        settingsManager.sdkSettings.subscribe(observer)
        val disposable = settingsManager.subscribeToActivityUpdates(activities)
        activities.onNext(ActivityManager.ApplicationStatus.Init())

        assertFalse(disposable.isDisposed)
        verify {
            observer(match { it.coreSettings.dataSource == "data_source" })
        }
    }

    @Test
    fun subscribeToActivityUpdates_Stops_Refreshing_After_Dispose() {
        config.sdkSettingsUrl = "https://localhost/"
        config.useRemoteSettings = true
        val observer = mockk<(SdkSettings) -> Unit>(relaxed = true)
        mockRemoteResponse(
            configureCoreSettingsDataObject {
                setDataSource("data_source")
            }
        )
        createSettingsManager()
        val activities = Observables.publishSubject<ActivityManager.ApplicationStatus>()

        settingsManager.sdkSettings.subscribe(observer)
        val disposable = settingsManager.subscribeToActivityUpdates(activities)
        disposable.dispose()
        activities.onNext(ActivityManager.ApplicationStatus.Init())

        verify(inverse = true) {
            observer(match { it.coreSettings.dataSource == "data_source" })
        }
    }

    @Test
    fun subscribeToActivityUpdates_Emits_Merged_Settings() {
        config.sdkSettingsUrl = "https://localhost/"
        config.useRemoteSettings = true
        val observer = mockk<(SdkSettings) -> Unit>(relaxed = true)
        mockAssetResponse(
            configureCoreSettingsDataObject {
                setDataSource("asset")
                setBatchSize(1)
            }
        )
        every { mockCache.resource } returns configureCoreSettingsDataObject {
            setDataSource("cache")
            setWifiOnly(true)
        }
        mockRemoteResponse(
            configureCoreSettingsDataObject {
                setDataSource("remote")
                setBatterySaver(true)
            }
        )
        createSettingsManager()
        val activities = Observables.publishSubject<ActivityManager.ApplicationStatus>()

        settingsManager.sdkSettings.subscribe(observer)
        settingsManager.subscribeToActivityUpdates(activities)

        activities.onNext(ActivityManager.ApplicationStatus.Init())

        verify(inverse = true) {
            observer(match {
                it.coreSettings.dataSource == "remote"
                        && it.coreSettings.batterySaver
                        && it.coreSettings.wifiOnly
                        && it.coreSettings.batchSize == 1
            })
        }
    }

    @Test
    fun mergeSettings_Ignores_Null_Or_Empty_Settings() {
        val localSettings = configureCoreSettingsDataObject {
            setDataSource("test")
        }

        val mergedSettings =
            SettingsManager.mergeSettings(localSettings, null, DataObject.EMPTY_OBJECT)

        assertTrue(mergedSettings.moduleSettings.size == 1)
        assertTrue(mergedSettings.moduleSettings.containsKey("core"))
        assertTrue(mergedSettings.moduleSettings["core"]?.getString("data_source") == "test")
    }

    @Test
    fun mergeSettings_Returns_DataObject_Merging_All_Settings() {
        val settings1 = configureCoreSettingsDataObject {
            setDataSource("testSource")
        }
        val settings2 = configureCoreSettingsDataObject {
            setBatterySaver(true)
        }
        val settings3 = configureCoreSettingsDataObject {
            setWifiOnly(true)
        }

        val mergedSettings = SettingsManager.mergeSettings(settings1, settings2, settings3)

        assertTrue(mergedSettings.moduleSettings.size == 1)
        assertTrue(mergedSettings.moduleSettings.containsKey("core"))
        assertTrue(mergedSettings.moduleSettings["core"]?.getString(CoreSettingsImpl.KEY_DATA_SOURCE) == "testSource")
        assertTrue(mergedSettings.moduleSettings["core"]?.getBoolean(CoreSettingsImpl.KEY_BATTERY_SAVER)!!)
        assertTrue(mergedSettings.moduleSettings["core"]?.getBoolean(CoreSettingsImpl.KEY_WIFI_ONLY)!!)
    }

    @Test
    fun mergeSettings_Returns_DataObject_Replacing_Clashes_With_Higher_Priority_Values() {
        val settings1 = configureCoreSettingsDataObject {
            setDataSource("testSource")
        }
        val settings2 = configureCoreSettingsDataObject {
            setDataSource("remoteSource")
        }
        val settings3 = configureCoreSettingsDataObject {
            setDataSource("programmaticSource")
        }

        val mergedSettings = SettingsManager.mergeSettings(settings1, settings2, settings3)

        assertTrue(mergedSettings.moduleSettings.size == 1)
        assertTrue(mergedSettings.moduleSettings.containsKey("core"))
        assertTrue(mergedSettings.moduleSettings["core"]?.getString(CoreSettingsImpl.KEY_DATA_SOURCE) == "programmaticSource")
    }

    @Test
    fun mergeSettings_Returns_DataObject_With_All_Modules_From_All_Settings() {
        val settings1 = DataObject.fromString(
            """
            {
               "module1" : { "key": "value" },
               "module2" : { "key": "value" }
            }
        """
        )
        val settings2 = DataObject.fromString(
            """
            {
               "module3" : { "key": "value" },
               "module4" : { "key": "value" }
            }
        """
        )
        val settings3 = DataObject.fromString(
            """
            {
               "module5" : { "key": "value" },
               "module6" : { "key": "value" }
            }
        """
        )

        val merged = SettingsManager.mergeSettings(settings1, settings2, settings3)

        for (i in 1..6) {
            assertEquals("value", merged.moduleSettings["module$i"]!!.getString("key"))
        }
    }

    @Test
    fun mergeSettings_Returns_DataObject_Merging_First_Level_Only() {
        val settings1 = DataObject.fromString("""
            {
                "module1" : { 
                    "object" : { 
                        "sub-key-1": 1
                    }
                }
            }
        """)
        val settings2 = DataObject.fromString("""
            {
                "module1" : { 
                    "key": "value",
                    "object" : { 
                        "sub-key-2": 2
                    }
                }
            }
        """)

        val merged = SettingsManager.mergeSettings(settings1, settings2)

        val module1 = merged.moduleSettings["module1"]!!
        val subDataObject = module1.getDataObject("object")!!
        assertEquals("value", module1.getString("key"))
        assertEquals(2, subDataObject.getInt("sub-key-2"))
        assertNull(subDataObject.get("sub-key-1"))
    }

    @Test
    fun mergeSettings_Does_Not_Throw_When_No_Settings() {
        val merged = SettingsManager.mergeSettings(null, null, null)

        assertEquals(SdkSettings(), merged)
    }

    @Test
    fun loadFromAsset_ReturnsDataObject_When_ValidFile() {
        config.localSdkSettingsFileName = "validSettings.json"

        val result = SettingsManager.loadFromAsset(config)

        assertNotNull(result)
        assertEquals(
            DataItem.int(6),
            result?.get("core")?.getDataObject()?.get("batch_size")
        )
    }

    @Test
    fun loadFromAsset_ReturnsNull_When_InvalidFile() {
        config.localSdkSettingsFileName = "invalidSettings.json"

        val result = SettingsManager.loadFromAsset(config)

        assertNull(result)
    }

    @Test
    fun shouldRefresh_Emits_When_AppStatus_Is_Init() {
        val appStatus = Observables.publishSubject<ActivityManager.ApplicationStatus>()
        val observer = mockk<(Unit) -> Unit>(relaxed = true)
        val refresher = mockk<ResourceRefresher<DataObject>>()
        every { refresher.shouldRefresh } returns true

        SettingsManager.shouldRefresh(appStatus, refresher)
            .subscribe(observer)
        appStatus.onNext(ActivityManager.ApplicationStatus.Init())

        verify {
            observer(Unit)
        }
    }

    @Test
    fun shouldRefresh_Emits_When_AppStatus_Is_Foregrounded() {
        val appStatus = Observables.publishSubject<ActivityManager.ApplicationStatus>()
        val observer = mockk<(Unit) -> Unit>(relaxed = true)
        val refresher = mockk<ResourceRefresher<DataObject>>()
        every { refresher.shouldRefresh } returns true

        SettingsManager.shouldRefresh(appStatus, refresher)
            .subscribe(observer)
        appStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())

        verify {
            observer(Unit)
        }
    }

    @Test
    fun shouldRefresh_Does_Not_Emit_When_AppStatus_Is_Backgrounded() {
        val appStatus = Observables.publishSubject<ActivityManager.ApplicationStatus>()
        val observer = mockk<(Unit) -> Unit>(relaxed = true)
        val refresher = mockk<ResourceRefresher<DataObject>>()
        every { refresher.shouldRefresh } returns true

        SettingsManager.shouldRefresh(appStatus, refresher)
            .subscribe(observer)
        appStatus.onNext(ActivityManager.ApplicationStatus.Backgrounded())

        verify(inverse = true) {
            observer(Unit)
        }
    }

    @Test
    fun shouldRefresh_Does_Not_Emit_When_Refresher_Should_Not_Refresh() {
        val appStatus = Observables.publishSubject<ActivityManager.ApplicationStatus>()
        val observer = mockk<(Unit) -> Unit>(relaxed = true)
        val refresher = mockk<ResourceRefresher<DataObject>>()
        every { refresher.shouldRefresh } returns false

        SettingsManager.shouldRefresh(appStatus, refresher)
            .subscribe(observer)
        appStatus.onNext(ActivityManager.ApplicationStatus.Init())
        appStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())
        appStatus.onNext(ActivityManager.ApplicationStatus.Backgrounded())

        verify(inverse = true) {
            observer(Unit)
        }
    }

    @Test
    fun refreshInterval_Does_Emit_When_Values_Are_Different() {
        val initialSettings = configureSdkWithCoreSettings {
            setRefreshInterval(10.seconds)
        }
        val observer = mockk<(TimeFrame) -> Unit>(relaxed = true)
        val settings = Observables.stateSubject(initialSettings)

        SettingsManager.refreshInterval(settings)
            .subscribe(observer)
        settings.onNext(
            initialSettings.copy(
                mapOf(
                    "core" to CoreSettingsBuilder()
                        .setRefreshInterval(60.seconds)
                        .build()
                )
            )
        )

        verify(exactly = 1) {
            observer(10.seconds)
        }
    }

    @Test
    fun refreshInterval_Does_Not_Emit_When_Values_Are_Same() {
        val initialSettings = configureSdkWithCoreSettings {
            setRefreshInterval(10.seconds)
        }
        val observer = mockk<(TimeFrame) -> Unit>(relaxed = true)
        val settings = Observables.stateSubject(initialSettings)

        SettingsManager.refreshInterval(settings)
            .subscribe(observer)
        settings.onNext(initialSettings)

        verify(exactly = 1) {
            observer(10.seconds)
        }
    }


    private fun createSettingsManager(
        config: TealiumConfig = this.config,
        mockNetworkHelper: NetworkHelper = this.mockNetworkHelper,
        mockCache: ResourceCache<DataObject> = this.mockCache,
        mockLogger: AlternateLogger = this.mockLogger,
    ): SettingsManager {
        settingsManager = SettingsManager(
            config,
            mockNetworkHelper,
            mockCache,
            logger = mockLogger,
        )
        return settingsManager
    }


    /**
     * Configures the CoreSettings in
     */
    private fun configureCoreSettingsDataObject(block: CoreSettingsBuilder.() -> CoreSettingsBuilder): DataObject {
        return configureSdkWithCoreSettings(block)
            .asDataItem()
            .getDataObject()!!
    }

    /**
     * Configures the CoreSettings in
     */
    private fun configureModuleSettingsDataObject(
        id: String,
        block: DataObject.Builder.() -> DataObject.Builder
    ): DataObject {
        return configureSdkSettings {
            configureModule(id, block)
        }.asDataItem()
            .getDataObject()!!
    }

    /**
     * Configures the CoreSettings in
     */
    private fun configureSdkWithCoreSettings(block: CoreSettingsBuilder.() -> CoreSettingsBuilder): SdkSettings {
        return configureSdkSettings {
            configureModule("core", block(CoreSettingsBuilder()).build())
        }
    }

    /**
     * Configures the CoreSettings in
     */
    private fun configureSdkSettingsDataObject(block: SdkSettingsBuilder.() -> SdkSettingsBuilder): DataObject {
        return block(SdkSettingsBuilder()).buildDataObject()
    }

    /**
     * Configures the CoreSettings in
     */
    private fun configureSdkSettings(block: SdkSettingsBuilder.() -> SdkSettingsBuilder): SdkSettings {
        return block(SdkSettingsBuilder()).build()
    }

    private class SdkSettingsBuilder {
        private val settings = mutableMapOf<String, DataObject>()

        fun configureModule(
            id: String,
            block: DataObject.Builder.() -> DataObject.Builder
        ): SdkSettingsBuilder = apply {
            val moduleSettings = block(DataObject.Builder()).build()

            settings[id] = moduleSettings
        }

        fun configureModule(
            id: String,
            dataObject: DataObject
        ): SdkSettingsBuilder = apply {
            settings[id] = dataObject
        }

        fun buildDataObject(): DataObject {
            return DataObject.create {
                settings.forEach {
                    put(it.key, it.value)
                }
            }
        }

        fun build(): SdkSettings {
            return SdkSettings(settings)
        }
    }

    /**
     * Mocks the response from [SettingsManager.loadFromAsset]
     *
     * If omitted, then [dataObject] will return the [DataObject] generated by the default SdkSettings.
     */
    private fun mockAssetResponse(
        dataObject: DataObject? = SdkSettings().asDataItem().getDataObject()!!
    ) {
        mockkObject(SettingsManager)
        every { SettingsManager.loadFromAsset(any()) } returns dataObject
    }

    /**
     * Mocks the response from a remote source
     *
     * If omitted, then [dataObject] will return the [DataObject] generated by the default SdkSettings.
     */
    private fun mockRemoteResponse(
        dataObject: DataObject = SdkSettings().asDataItem().getDataObject()!!,
    ) {
        mockNetworkHelper.mockGetDataItemConvertibleSuccess(
            dataObject, DataObject.Converter
        )
    }
}
