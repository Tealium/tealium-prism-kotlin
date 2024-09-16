package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.settings.ModuleSettingsBuilder
import com.tealium.core.api.tracking.Dispatch
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.Parameterized

@RunWith(Enclosed::class)
open class ConnectivityCollectorTests {

    @MockK
    protected lateinit var connectivity: Connectivity

    protected lateinit var connectivityCollector: ConnectivityCollector

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { connectivity.isConnected() } returns true
        every { connectivity.connectionType() } returns Connectivity.ConnectivityType.WIFI

        connectivityCollector = ConnectivityCollector(connectivity)
    }

    @RunWith(JUnit4::class)
    class StandardTests : ConnectivityCollectorTests() {

        @Test
        fun factory_ReturnsModule_When_Enabled() {
            val context = mockk<TealiumContext>(relaxed = true)
            every { context.network } returns mockk()

            assertNotNull(
                ConnectivityCollector.Factory.create(
                    context, TealiumBundle.EMPTY_BUNDLE
                )
            )
        }

        @Test
        fun name_Matches_Factory() {
            assertEquals(ConnectivityCollector.Factory.id, connectivityCollector.id)
        }

        @Test
        fun name_Returns_Connectivity() {
            assertEquals("Connectivity", connectivityCollector.id)
        }

        @Test
        fun version_Returns_LibraryVersion() {
            assertEquals(BuildConfig.TEALIUM_LIBRARY_VERSION, connectivityCollector.version)
        }

        @Test
        fun updateSettings_ReturnsModule_When_NotEnabled() {
            assertSame(
                connectivityCollector,
                connectivityCollector.updateSettings(
                    ModuleSettingsBuilder()
                        .setEnabled(false)
                        .build()
                )
            )
        }

        @Test
        fun updateSettings_ReturnsModule_When_Enabled() {
            assertSame(
                connectivityCollector,
                connectivityCollector.updateSettings(TealiumBundle.EMPTY_BUNDLE)
            )
        }
    }

    @RunWith(Parameterized::class)
    class ConnectivityTypeTests(
        private val connectivityType: Connectivity.ConnectivityType,
        private val expected: String
    ) : ConnectivityCollectorTests() {

        companion object {
            @JvmStatic
            @Parameterized.Parameters
            fun connectivityTypeParams() = arrayOf(
                arrayOf(Connectivity.ConnectivityType.WIFI, "wifi"),
                arrayOf(Connectivity.ConnectivityType.CELLULAR, "cellular"),
                arrayOf(Connectivity.ConnectivityType.ETHERNET, "ethernet"),
                arrayOf(
                    Connectivity.ConnectivityType.VPN,
                    "vpn"
                ), // TODO - consider whether these are necessary
                arrayOf(
                    Connectivity.ConnectivityType.BLUETOOTH,
                    "bluetooth"
                ), // TODO - consider whether these are necessary
            )
        }

        @Test
        fun collect_ReturnsBundle_Containing_ConnectivityType() {
            every { connectivity.connectionType() } returns connectivityType

            val bundle = connectivityCollector.collect()
            assertEquals(expected, bundle.getString(Dispatch.Keys.CONNECTION_TYPE)!!)
        }
    }
}