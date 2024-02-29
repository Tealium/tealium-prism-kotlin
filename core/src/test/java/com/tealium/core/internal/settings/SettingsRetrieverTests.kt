package com.tealium.core.internal.settings

import com.tealium.core.TealiumConfig
import com.tealium.core.api.DataStore
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.internal.SdkSettings
import com.tealium.core.internal.observables.Subscription
import com.tealium.tests.common.getDefaultConfig
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsRetrieverTests {

    @RelaxedMockK
    lateinit var mockNetworkUtilities: NetworkUtilities

    @RelaxedMockK
    lateinit var mockSettingsDataStore: DataStore

    @RelaxedMockK
    lateinit var mockLogger: Logger

    private lateinit var config: TealiumConfig

    private lateinit var settingsRetriever: SettingsRetriever

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        val app = RuntimeEnvironment.getApplication()

        config = getDefaultConfig(app)
        settingsRetriever =
            SettingsRetriever(config, mockNetworkUtilities, mockSettingsDataStore, mockLogger)
    }

    @Test
    fun validLoadLocalSettings() {
        config.localSdkSettingsFileName = "validSettings.json"
        val result = settingsRetriever.loadLocalSettings()

        assertNotNull(result)
        assertEquals(
            TealiumValue.int(6),
            result?.moduleSettings?.get("core")?.bundle?.get("batch_size")
        )
    }

    @Test
    fun nullLoadLocalSettingsForInvalidFile() {
        config.localSdkSettingsFileName = "invalidSettings.json"

        val result = settingsRetriever.loadLocalSettings()

        assertNull(result)
    }

    @Test
    fun validRemoteSettings() {
        config.useRemoteSettings = true
        config.sdkSettingsUrl = "https://someurl.com"
        val completion: (SdkSettings?) -> Unit = mockk(relaxed = true)

        val networkCapture = slot<(TealiumBundle?) -> Unit>()
        every {
            mockNetworkUtilities.networkHelper.getTealiumBundle(
                any(),
                any(),
                capture(networkCapture)
            )
        } answers {
            networkCapture.captured.invoke(TealiumBundle.create {
                put("core", TealiumBundle.create {
                    put(CoreSettings.KEY_BATCH_SIZE, 9)
                    put(CoreSettings.KEY_DEEPLINK_TRACKING_ENABLED, false)
                    put(CoreSettings.KEY_LOG_LEVEL, "info")
                    put(CoreSettings.KEY_BATTERY_SAVER, false)
                    put(CoreSettings.KEY_WIFI_ONLY, false)
                    put(CoreSettings.KEY_REFRESH_INTERVAL, 3)
                })
            })
            Subscription()
        }

        settingsRetriever.fetchRemoteSettings(completion)
        verify(timeout = 1000) {
            completion(match { result ->
                val coreBundle = result.moduleSettings["core"]!!.bundle

                coreBundle.get(CoreSettings.KEY_BATCH_SIZE) == TealiumValue.int(9)
                        && coreBundle.get(CoreSettings.KEY_REFRESH_INTERVAL) == TealiumValue.int(3)
            })
        }
    }
}