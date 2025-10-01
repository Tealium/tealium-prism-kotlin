package com.tealium.prism.core.internal.settings

import android.app.Application
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataObject
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.days
import com.tealium.prism.core.api.misc.TimeFrameUtils.minutes
import com.tealium.prism.core.api.misc.TimeFrameUtils.seconds
import com.tealium.prism.core.api.network.NetworkHelper
import com.tealium.prism.core.api.network.ResourceCache
import com.tealium.prism.core.api.network.ResourceRefresher
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.rules.Condition.Companion.isDefined
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.rules.matches
import com.tealium.prism.core.api.settings.CoreSettingsBuilder
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.internal.network.mockGetDataItemConvertibleSuccess
import com.tealium.prism.core.internal.rules.LoadRule
import com.tealium.tests.common.SystemLogger
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

    private lateinit var logger: Logger
    private lateinit var config: TealiumConfig
    private lateinit var settingsManager: SettingsManager
    private lateinit var settingsSubject: StateSubject<SdkSettings>

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        logger = SystemLogger
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

        assertEquals(CoreSettingsImpl(), settings.core)
        assertTrue(settings.modules.isEmpty())
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
            config, mockNetworkHelper, 10.minutes, mockCache, logger
        )

        assertNotNull(refresher)
    }

    @Test
    fun init_Does_Not_Create_ResourceRefresher_When_RemoteSettings_Disabled() {
        config.useRemoteSettings = false
        val refresher = SettingsManager.createResourceRefresher(
            config, mockNetworkHelper, 10.minutes, mockCache, logger
        )

        assertNull(refresher)
    }

    @Test
    fun init_Does_Not_Create_ResourceRefresher_When_Missing_Url() {
        config.sdkSettingsUrl = null
        val refresher = SettingsManager.createResourceRefresher(
            config, mockNetworkHelper, 10.minutes, mockCache, logger
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
                setVisitorIdentityKey("identity")
            }
        )
        createSettingsManager()
        val activities = Observables.publishSubject<ActivityManager.ApplicationStatus>()

        settingsManager.sdkSettings.subscribe(observer)
        val disposable = settingsManager.subscribeToActivityUpdates(activities)
        activities.onNext(ActivityManager.ApplicationStatus.Init())

        assertFalse(disposable.isDisposed)
        verify {
            observer(match { it.core.visitorIdentityKey == "identity" })
        }
    }

    @Test
    fun subscribeToActivityUpdates_Stops_Refreshing_After_Dispose() {
        config.sdkSettingsUrl = "https://localhost/"
        config.useRemoteSettings = true
        val observer = mockk<(SdkSettings) -> Unit>(relaxed = true)
        mockRemoteResponse(
            configureCoreSettingsDataObject {
                setVisitorIdentityKey("identity")
            }
        )
        createSettingsManager()
        val activities = Observables.publishSubject<ActivityManager.ApplicationStatus>()

        settingsManager.sdkSettings.subscribe(observer)
        val disposable = settingsManager.subscribeToActivityUpdates(activities)
        disposable.dispose()
        activities.onNext(ActivityManager.ApplicationStatus.Init())

        verify(inverse = true) {
            observer(match { it.core.visitorIdentityKey == "identity" })
        }
    }

    @Test
    fun subscribeToActivityUpdates_Emits_Merged_Settings() {
        config.sdkSettingsUrl = "https://localhost/"
        config.useRemoteSettings = true
        val observer = mockk<(SdkSettings) -> Unit>(relaxed = true)
        mockAssetResponse(
            configureCoreSettingsDataObject {
                setVisitorIdentityKey("asset")
                setMaxQueueSize(1)
            }
        )
        every { mockCache.resource } returns configureCoreSettingsDataObject {
            setVisitorIdentityKey("cache")
            setLogLevel(LogLevel.WARN)
        }
        mockRemoteResponse(
            configureCoreSettingsDataObject {
                setVisitorIdentityKey("remote")
                setExpiration(10.minutes)
            }
        )
        createSettingsManager()
        val activities = Observables.publishSubject<ActivityManager.ApplicationStatus>()

        settingsManager.sdkSettings.subscribe(observer)
        settingsManager.subscribeToActivityUpdates(activities)

        activities.onNext(ActivityManager.ApplicationStatus.Init())

        verify(inverse = true) {
            observer(match {
                it.core.visitorIdentityKey == "remote"
                        && it.core.logLevel == LogLevel.WARN
                        && it.core.expiration == 10.minutes
                        && it.core.maxQueueSize == 1
            })
        }
    }

    @Test
    fun mergeSettings_Ignores_Null_Or_Empty_Settings() {
        val localSettings = configureCoreSettingsDataObject {
            setVisitorIdentityKey("test")
        }

        val mergedSettings =
            SettingsManager.mergeSettings(localSettings, null, DataObject.EMPTY_OBJECT)
        val sdkSettings = SdkSettings.fromDataObject(mergedSettings)
        assertTrue(sdkSettings.core.visitorIdentityKey == "test")
    }

    @Test
    fun mergeSettings_Merges_All_Core_Settings() {
        val settings1 = configureCoreSettingsDataObject {
            setVisitorIdentityKey("identity")
        }
        val settings2 = configureCoreSettingsDataObject {
            setExpiration(2.days)
        }
        val settings3 = configureCoreSettingsDataObject {
            setMaxQueueSize(10)
        }

        val mergedSettings = SettingsManager.mergeSettings(settings1, settings2, settings3)
        val sdkSettings = SdkSettings.fromDataObject(mergedSettings)

        assertEquals("identity", sdkSettings.core.visitorIdentityKey)
        assertEquals(172_800.seconds, sdkSettings.core.expiration)
        assertEquals(10, sdkSettings.core.maxQueueSize)
    }

    @Test
    fun mergeSettings_Merges_Core_Settings_Replacing_Clashes_With_Higher_Priority_Values() {
        val settings1 = configureCoreSettingsDataObject {
            setVisitorIdentityKey("testIdentity")
        }
        val settings2 = configureCoreSettingsDataObject {
            setVisitorIdentityKey("remoteIdentity")
        }
        val settings3 = configureCoreSettingsDataObject {
            setVisitorIdentityKey("programmaticIdentity")
        }

        val mergedSettings = SettingsManager.mergeSettings(settings1, settings2, settings3)
        val sdkSettings = SdkSettings.fromDataObject(mergedSettings)

        assertTrue(sdkSettings.core.visitorIdentityKey == "programmaticIdentity")
    }

    @Test
    fun mergeSettings_Merges_All_Modules_From_All_Settings() {
        val settings1 = DataObject.fromString(
            """
            {
                "modules" : {
                   "module1" : { "module_type": "module1", "enabled": true },
                   "module2" : { "module_type": "module2", "enabled": true }
                }
            }
        """
        )
        val settings2 = DataObject.fromString(
            """
            {
                "modules" : {
                   "module3" : { "module_type": "module3", "enabled": true },
                   "module4" : { "module_type": "module4", "enabled": true }
                }
            }
        """
        )
        val settings3 = DataObject.fromString(
            """
            {
                "modules" : {
                   "module5" : { "module_type": "module5", "enabled": true },
                   "module6" : { "module_type": "module6", "enabled": true }
                }
            }
        """
        )

        val merged = SettingsManager.mergeSettings(settings1, settings2, settings3)
        val sdkSettings = SdkSettings.fromDataObject(merged)

        for (i in 1..6) {
            assertTrue(sdkSettings.modules["module$i"]!!.enabled)
        }
    }

    @Test
    fun mergeSettings_Deep_Merges_Modules_But_Shallow_Merges_Configuration_Object() {
        val settings1 = DataObject.fromString(
            """
            {
                "modules" : {
                    "module1" : {
                        "module_type": "module1",
                        "configuration" : {
                            "sub-key-1": 1,
                            "sub-obj": {
                                "sub-key-1": 1
                            } 
                        }
                    }
                }
            }
        """
        )
        val settings2 = DataObject.fromString(
            """
            {
                "modules" : {
                    "module1" : {
                        "module_type": "module1",
                        "enabled": true,
                        "configuration" : {
                            "sub-key-2": 2,
                            "sub-obj": {
                                "sub-key-2": 2
                            } 
                        }
                    }
                }
            }
        """
        )

        val merged = SettingsManager.mergeSettings(settings1, settings2)
        val sdkSettings = SdkSettings.fromDataObject(merged)

        val module1 = sdkSettings.modules["module1"]!!
        assertTrue(module1.enabled)

        val configuration = module1.configuration
        assertEquals(1, configuration.getInt("sub-key-1"))
        assertEquals(2, configuration.getInt("sub-key-2"))

        val subConfiguration = configuration.getDataObject("sub-obj")!!
        assertNull(subConfiguration.get("sub-key-1"))
        assertEquals(2, subConfiguration.getInt("sub-key-2"))
    }

    @Test
    fun mergeSettings_Merges_All_Transformations_From_All_Settings() {
        val baseSettings =
            TransformationSettings("0", "0", setOf(TransformationScope.AllDispatchers))
        val settings1 = DataObject.fromString(
            """
            {
                "transformations" : {
                   "transformation1" : ${baseSettings.copy("1", "1").asDataItem()},
                   "transformation2" : ${baseSettings.copy("2", "2").asDataItem()}
                }
            }
        """
        )
        val settings2 = DataObject.fromString(
            """
            {
                "transformations" : {
                   "transformation3" : ${baseSettings.copy("3", "3").asDataItem()},
                   "transformation4" : ${baseSettings.copy("4", "4").asDataItem()}
                }
            }
        """
        )
        val settings3 = DataObject.fromString(
            """
            {
                "transformations" : {
                   "transformation5" : ${baseSettings.copy("5", "5").asDataItem()},
                   "transformation6" : ${baseSettings.copy("6", "6").asDataItem()}
                }
            }
        """
        )

        val merged = SettingsManager.mergeSettings(settings1, settings2, settings3)
        val sdkSettings = SdkSettings.fromDataObject(merged)

        for (i in 1..6) {
            assertEquals(
                baseSettings.copy("$i", "$i"),
                sdkSettings.transformations["transformation$i"]
            )
        }
    }

    @Test
    fun mergeSettings_Deep_Merges_Transformations_But_Shallow_Merges_Configuration_Object() {
        val settings1 = DataObject.fromString(
            """
            {
                "transformations" : {
                    "transformer1-transformation1" : {
                        "transformation_id": "transformation1",
                        "transformer_id": "transformer1",
                        "scopes": ["all"],
                        "configuration" : {
                            "sub-key-1": 1,
                            "sub-obj": {
                                "sub-key-1": 1
                            } 
                        }
                    }
                }
            }
        """
        )
        val settings2 = DataObject.fromString(
            """
            {
                "transformations" : {
                    "transformer1-transformation1" : {
                        "transformation_id": "transformation1",
                        "transformer_id": "transformer1",
                        "scopes": ["aftercollectors"],
                        "configuration" : {
                            "sub-key-2": 2,
                            "sub-obj": {
                                "sub-key-2": 2
                            } 
                        }
                    }
                }
            }
        """
        )

        val merged = SettingsManager.mergeSettings(settings1, settings2)
        val settings = SdkSettings.fromDataObject(merged)

        val transformation1 = settings.transformations["transformer1-transformation1"]!!
        val scopes = transformation1.scope
        assertEquals(1, transformation1.scope.size)
        assertEquals(TransformationScope.AfterCollectors, transformation1.scope.elementAt(0))

        val configuration = transformation1.configuration
        assertEquals(1, configuration.getInt("sub-key-1"))
        assertEquals(2, configuration.getInt("sub-key-2"))

        val subConfiguration = configuration.getDataObject("sub-obj")!!
        assertNull(subConfiguration.get("sub-key-1"))
        assertEquals(2, subConfiguration.getInt("sub-key-2"))
    }

    @Test
    fun mergeSettings_Merges_All_Barriers_From_All_Settings() {
        val settings1 = DataObject.fromString(
            """
            {
                "barriers" : {
                   "barrier1" : { "key": "value" },
                   "barrier2" : { "key": "value" }
                }
            }
        """
        )
        val settings2 = DataObject.fromString(
            """
            {
                "barriers" : {
                   "barrier3" : { "key": "value" },
                   "barrier4" : { "key": "value" }
                }
            }
        """
        )
        val settings3 = DataObject.fromString(
            """
            {
                "barriers" : {
                   "barrier5" : { "key": "value" },
                   "barrier6" : { "key": "value" }
                }
            }
        """
        )

        val merged = SettingsManager.mergeSettings(settings1, settings2, settings3)

        for (i in 1..6) {
            assertEquals("value", merged.barriers.getDataObject("barrier$i")!!.getString("key"))
        }
    }

    @Test
    fun mergeSettings_Deep_Merges_Barriers_But_Shallow_Merges_Configuration_Object() {
        val settings1 = DataObject.fromString(
            """
            {
                "barriers" : {
                    "barrier1" : {
                        "scopes": ["all"],
                        "configuration" : {
                            "sub-key-1": 1,
                            "sub-obj": {
                                "sub-key-1": 1
                            } 
                        }
                    }
                }
            }
        """
        )
        val settings2 = DataObject.fromString(
            """
            {
                "barriers" : {
                    "barrier1" : {
                        "scopes": ["dispatcher"],
                        "configuration" : {
                            "sub-key-2": 2,
                            "sub-obj": {
                                "sub-key-2": 2
                            } 
                        }
                    }
                }
            }
        """
        )

        val merged = SettingsManager.mergeSettings(settings1, settings2)

        val barrier1 = merged.barriers.getDataObject("barrier1")!!
        val scopes = barrier1.getDataList("scopes")!!
        assertEquals(1, scopes.size)
        assertEquals("dispatcher", scopes.getString(0))

        val configuration = barrier1.getDataObject("configuration")!!
        assertEquals(1, configuration.getInt("sub-key-1"))
        assertEquals(2, configuration.getInt("sub-key-2"))

        val subConfiguration = configuration.getDataObject("sub-obj")!!
        assertNull(subConfiguration.get("sub-key-1"))
        assertEquals(2, subConfiguration.getInt("sub-key-2"))
    }

    @Test
    fun mergeSettings_Merges_All_LoadRules_From_All_Settings() {
        val baseRule = LoadRule("0", Rule.just(isDefined(null, "var")))
        val settings1 = DataObject.fromString(
            """
            {
                "load_rules" : {
                   "load_rule1" : ${baseRule.copy("1").asDataItem()},
                   "load_rule2" : ${baseRule.copy("2").asDataItem()}
                }
            }
        """
        )
        val settings2 = DataObject.fromString(
            """
            {
                "load_rules" : {
                   "load_rule3" : ${baseRule.copy("3").asDataItem()},
                   "load_rule4" : ${baseRule.copy("4").asDataItem()}
                }
            }
        """
        )
        val settings3 = DataObject.fromString(
            """
            {
                "load_rules" : {
                   "load_rule5" : ${baseRule.copy("5").asDataItem()},
                   "load_rule6" : ${baseRule.copy("6").asDataItem()}
                }
            }
        """
        )

        val merged = SettingsManager.mergeSettings(settings1, settings2, settings3)
        val settings = SdkSettings.fromDataObject(merged)

        for (i in 1..6) {
            assertEquals(
                baseRule.copy("$i"),
                settings.loadRules["load_rule$i"]
            )
        }
    }

    @Test
    fun mergeSettings_Merges_All_LoadRules_Properties() {
        val settings1 = DataObject.fromString(
            """
            {
                "load_rules" : {
                    "load_rule1" : {
                        "conditions": {
                            "variable": "var-1",
                            "operator": "defined"
                        },
                        "id": "load_rule1"
                    }
                }
            }
        """
        )
        val settings2 = DataObject.fromString(
            """
            {
                "load_rules" : {
                    "load_rule1" : {
                        "conditions": {
                            "variable": "var-2",
                            "operator": "defined"
                        },
                        "id": "load_rule2"
                    }
                }
            }
        """
        )

        val merged = SettingsManager.mergeSettings(settings1, settings2)
        val settings = SdkSettings.fromDataObject(merged)

        val loadRule1 = settings.loadRules["load_rule1"]!!
        assertEquals("load_rule2", loadRule1.id)

        val conditions = loadRule1.conditions
        assertTrue(conditions.matches(DataObject.create { put("var-2", "defined") }))
        assertFalse(conditions.matches(DataObject.create { put("var-1", "defined") }))
    }

    @Test
    fun mergeSettings_Does_Not_Throw_When_No_Settings() {
        val merged = SettingsManager.mergeSettings(null, null, null)

        assertEquals(DataObject.EMPTY_OBJECT, merged)
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
                core = CoreSettingsImpl(refreshInterval = 60.seconds)
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
        mockLogger: Logger = this.logger,
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
        return configureSdkSettingsDataObject {
            configureCore(block)
        }
    }

    /**
     * Configures the CoreSettings in
     */
    private fun configureSdkWithCoreSettings(block: CoreSettingsBuilder.() -> CoreSettingsBuilder): SdkSettings {
        return configureSdkSettings {
            configureCore(block)
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
        private val core = CoreSettingsBuilder()
        private val modules = mutableMapOf<String, DataObject>()

        fun configureCore(
            block: CoreSettingsBuilder.() -> CoreSettingsBuilder
        ) = apply {
            block.invoke(core)
        }

        fun configureModule(
            id: String,
            block: DataObject.Builder.() -> DataObject.Builder
        ): SdkSettingsBuilder = apply {
            val moduleSettings = block(DataObject.Builder()).build()

            modules[id] = moduleSettings
        }

        fun configureModule(
            id: String,
            dataObject: DataObject
        ): SdkSettingsBuilder = apply {
            modules[id] = dataObject
        }

        fun buildDataObject(): DataObject {
            return DataObject.create {
                put(CoreSettingsImpl.MODULE_NAME, core.build())
                put(SdkSettings.KEY_MODULES, modules.asDataObject())
            }
        }

        fun build(): SdkSettings {
            return SdkSettings.fromDataObject(
                buildDataObject()
            )
        }
    }

    /**
     * Mocks the response from [SettingsManager.loadFromAsset]
     *
     * If omitted, then [dataObject] will return the [DataObject] generated by the default SdkSettings.
     */
    private fun mockAssetResponse(
        dataObject: DataObject? = DataObject.EMPTY_OBJECT
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
        dataObject: DataObject = DataObject.EMPTY_OBJECT
    ) {
        mockNetworkHelper.mockGetDataItemConvertibleSuccess(
            dataObject, DataObject.Converter
        )
    }

    private val DataObject.barriers: DataObject
        get() = getDataObject(SdkSettings.KEY_BARRIERS) ?: DataObject.EMPTY_OBJECT
}
