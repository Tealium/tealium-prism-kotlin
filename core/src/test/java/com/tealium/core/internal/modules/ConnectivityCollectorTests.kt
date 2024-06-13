package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.Dispatch
import com.tealium.core.api.network.Connectivity
import com.tealium.core.internal.settings.ModuleSettingsImpl
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
        fun factory_ReturnsNull_When_NotEnabled() {
            assertNull(ConnectivityCollector.Factory.create(mockk(), ModuleSettingsImpl(false)))
        }

        @Test
        fun factory_ReturnsModule_When_Enabled() {
            val context = mockk<TealiumContext>(relaxed = true)
            every { context.network } returns mockk()

            assertNotNull(ConnectivityCollector.Factory.create(context, ModuleSettingsImpl(true)))
        }

        @Test
        fun name_Matches_Factory() {
            assertEquals(ConnectivityCollector.Factory.name, connectivityCollector.name)
        }

        @Test
        fun name_Returns_Connectivity() {
            assertEquals("Connectivity", connectivityCollector.name)
        }

        @Test
        fun version_Returns_LibraryVersion() {
            assertEquals(BuildConfig.TEALIUM_LIBRARY_VERSION, connectivityCollector.version)
        }

        @Test
        fun updateSettings_ReturnsNull_When_NotEnabled() {
            assertNull(connectivityCollector.updateSettings(ModuleSettingsImpl(false)))
        }

        @Test
        fun updateSettings_ReturnsModule_When_Enabled() {
            assertSame(
                connectivityCollector,
                connectivityCollector.updateSettings(ModuleSettingsImpl(true))
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
                arrayOf(Connectivity.ConnectivityType.VPN, "vpn"), // TODO - consider whether these are necessary
                arrayOf(Connectivity.ConnectivityType.BLUETOOTH, "bluetooth"), // TODO - consider whether these are necessary
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