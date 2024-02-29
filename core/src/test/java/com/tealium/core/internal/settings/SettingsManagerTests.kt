package com.tealium.core.internal.settings

import android.app.Application
import com.tealium.core.TealiumConfig
import com.tealium.core.api.DataStore
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumDeserializable
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.api.settings.CoreSettingsBuilder
import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.internal.SdkSettings
import com.tealium.tests.common.getDefaultConfig
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SettingsManagerTests {

    @MockK
    lateinit var app: Application

    @RelaxedMockK
    lateinit var mockNetworkUtilities: NetworkUtilities

    @RelaxedMockK
    lateinit var mockSettingsDataStore: DataStore

    @RelaxedMockK
    lateinit var mockLogger: Logger

    @RelaxedMockK
    lateinit var mockSettingsProvider: InternalSettingsProvider

    private lateinit var config: TealiumConfig

    private lateinit var settingsRetriever: SettingsRetriever

    private lateinit var settingsManager: SettingsManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { app.filesDir } returns File("")
        every { app.applicationContext } returns app

        config = getDefaultConfig(app)
        settingsRetriever =
            spyk(SettingsRetriever(config, mockNetworkUtilities, mockSettingsDataStore, mockLogger))

        settingsManager = SettingsManager(
            config,
            mockNetworkUtilities,
            mockSettingsDataStore,
            mockSettingsProvider,
            mockLogger,
            settingsRetriever = settingsRetriever
        )
    }

    @Test
    fun loadDefaultSetOfSdkSettings() {
        settingsManager = SettingsManager(
            config,
            mockNetworkUtilities,
            mockSettingsDataStore,
            mockSettingsProvider,
            mockLogger,
            settingsRetriever = settingsRetriever
        )


        coEvery { settingsRetriever.loadLocalSettings() } returns null
        coEvery { settingsRetriever.loadLocalSettings() } returns null

        val settings = settingsManager.loadSettings()

        assertTrue(settings.moduleSettings.isEmpty())
    }

    @Test
    fun loadLocalSettingsWhenUseRemoteSettingsIsFalse() {
        settingsManager = SettingsManager(
            config,
            mockNetworkUtilities,
            mockSettingsDataStore,
            mockSettingsProvider,
            mockLogger,
            settingsRetriever = settingsRetriever
        )

        coEvery { settingsRetriever.loadLocalSettings() } returns SdkSettings()

        settingsManager.loadSettings()

        verify(exactly = 0) { settingsRetriever.fetchRemoteSettings(any()) }
        verify { settingsRetriever.loadLocalSettings() }
    }

    @Test
    fun loadSettingsCallsSettingsRetriever() {
        config.useRemoteSettings = true

        settingsManager = SettingsManager(
            config,
            mockNetworkUtilities,
            mockSettingsDataStore,
            mockSettingsProvider,
            mockLogger,
            settingsRetriever = settingsRetriever
        )

        coEvery { settingsRetriever.loadLocalSettings() } returns SdkSettings()

        settingsManager.loadSettings()

        verify { settingsRetriever.fetchRemoteSettings(any()) }
        verify { settingsRetriever.loadLocalSettings() }
    }

    @Test
    fun loadSettingsSendsUpdatedSdkSettings() {
        config.useRemoteSettings = true
        val remoteSettings = mockk<SdkSettings>(relaxed = true)
        val localSettings = mockk<SdkSettings>(relaxed = true)

        coEvery { settingsRetriever.loadLocalSettings() } returns localSettings

        settingsManager = SettingsManager(
            config,
            mockNetworkUtilities,
            mockSettingsDataStore,
            mockSettingsProvider,
            mockLogger,
            settingsRetriever = settingsRetriever
        )

        settingsManager.loadSettings()

        verify { mockSettingsProvider.updateSdkSettings(any()) }
    }

    @Test
    fun mergeSettingsReturnsValidSettingsForOnlyLocal() {
        val localSettings =
            SdkSettings(moduleSettings = mapOf("core" to ModuleSettingsImpl(bundle = TealiumBundle.create {
                put("data_source", "test")
            })))

        settingsManager = SettingsManager(
            config,
            mockNetworkUtilities,
            mockSettingsDataStore,
            mockSettingsProvider,
            mockLogger,
            settingsRetriever = settingsRetriever
        )

        val mergedSettings = settingsManager.mergeSettings(localSettings = localSettings)

        assertTrue(mergedSettings.moduleSettings.size == 1)
        assertTrue(mergedSettings.moduleSettings.containsKey("core"))
        assertTrue(mergedSettings.moduleSettings["core"]?.bundle?.getString("data_source") == "test")
    }

    @Test
    fun mergeSettingsValidMergeOfLocalAndRemote() {
        val localSettings =
            SdkSettings(
                moduleSettings = mapOf(
                    "core" to ModuleSettingsImpl(bundle = TealiumBundle.create {
                        put(CoreSettings.KEY_DATA_SOURCE, "testSource")
                    })
                )
            )
        val remoteSettings =
            SdkSettings(
                moduleSettings = mapOf(
                    "core" to ModuleSettingsImpl(bundle = TealiumBundle.create {
                        put(CoreSettings.KEY_DATA_SOURCE, "remoteSource")
                    })
                )
            )

        settingsManager = SettingsManager(
            config,
            mockNetworkUtilities,
            mockSettingsDataStore,
            mockSettingsProvider,
            mockLogger,
            settingsRetriever = settingsRetriever
        )

        val mergedSettings = settingsManager.mergeSettings(
            remoteSettings = remoteSettings,
            localSettings = localSettings
        )

        assertTrue(mergedSettings.moduleSettings.size == 1)
        assertTrue(mergedSettings.moduleSettings.containsKey("core"))
        assertTrue(mergedSettings.moduleSettings["core"]?.bundle?.getString(CoreSettings.KEY_DATA_SOURCE) == "remoteSource")
    }

    @Test
    fun mergeSettingsValidMergeOfLocalRemoteAndProgrammatic() {
        val localSettings =
            SdkSettings(
                moduleSettings = mapOf(
                    "core" to ModuleSettingsImpl(bundle = TealiumBundle.create {
                        put(CoreSettings.KEY_DATA_SOURCE, "testSource")
                    })
                )
            )
        val remoteSettings =
            SdkSettings(
                moduleSettings = mapOf(
                    "core" to ModuleSettingsImpl(bundle = TealiumBundle.create {
                        put(CoreSettings.KEY_DATA_SOURCE, "remoteSource")
                        put(CoreSettings.KEY_LOG_LEVEL, "TRACE")
                    })
                )
            )

        config.addModuleSettings(CoreSettingsBuilder().setDataSource("programmaticSource"))

        settingsManager = SettingsManager(
            config,
            mockNetworkUtilities,
            mockSettingsDataStore,
            mockSettingsProvider,
            mockLogger,
            settingsRetriever = settingsRetriever
        )

        val mergedSettings = settingsManager.mergeSettings(
            remoteSettings = remoteSettings,
            localSettings = localSettings
        )

        assertTrue(mergedSettings.moduleSettings.size == 1)
        assertTrue(mergedSettings.moduleSettings.containsKey("core"))
        assertTrue(mergedSettings.moduleSettings["core"]?.bundle?.getString(CoreSettings.KEY_DATA_SOURCE) == "programmaticSource")
        assertTrue(mergedSettings.moduleSettings["core"]?.bundle?.getString(CoreSettings.KEY_LOG_LEVEL) == "TRACE")
    }

    @Test
    fun mergeSettingsValidFirstLevelOnly() {
        val localMap = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3")
        val localSettings =
            SdkSettings(moduleSettings = mapOf("test" to TestSettings(property1 = localMap)))
        val remoteMap = mapOf("key2" to "value22", "key4" to "value4")
        val remoteSettings =
            SdkSettings(moduleSettings = mapOf("test" to TestSettings(property1 = remoteMap)))

        val testMap = mapOf("key2" to "value222", "key4" to "value44", "key5" to "value5")
        config.modulesSettings["test"] =
            TestSettings(property1 = testMap)

        settingsManager = SettingsManager(
            config,
            mockNetworkUtilities,
            mockSettingsDataStore,
            mockSettingsProvider,
            mockLogger,
            settingsRetriever = settingsRetriever
        )

        val mergedSettings = settingsManager.mergeSettings(
            remoteSettings = remoteSettings,
            localSettings = localSettings
        )

        assertTrue(mergedSettings.moduleSettings.containsKey("test"))

        val prop1 = mergedSettings.moduleSettings["test"]?.bundle?.getBundle("property1")

        assertEquals(TealiumBundle.fromMap(testMap), prop1)
        assertNull(prop1?.getString("key1"))
        assertNull(prop1?.getString("key3"))
    }
}

data class TestSettings(
    override var enabled: Boolean = true,
    override val dependencies: List<Any> = emptyList(),
    var property1: Map<String, String>
) : ModuleSettings {
    override val bundle: TealiumBundle
        get() = TealiumBundle.create {
            put("property1", TealiumBundle.fromMap(property1))
        }

    object Deserializer : TealiumDeserializable<TestSettings> {
        override fun deserialize(value: TealiumValue): TestSettings? {
            val bundle = value.getBundle() ?: return null
            val p1 = bundle.getBundle("property1")
            val map = p1?.associate {
                it.key to it.value.getString()
            } as Map<String, String>

            return TestSettings(property1 = map)
        }
    }
}