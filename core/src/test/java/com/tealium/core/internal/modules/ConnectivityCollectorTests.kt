package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.network.Connectivity.ConnectivityType
import com.tealium.core.api.network.Connectivity.Status
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.Parameterized

@RunWith(Enclosed::class)
open class ConnectivityCollectorTests {

    @MockK
    protected lateinit var connectivity: StateSubject<Status>

    protected lateinit var connectivityCollector: ConnectivityCollector

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        connectivity = Observables.stateSubject(Status.Connected(ConnectivityType.WIFI))

        connectivityCollector = ConnectivityCollector(connectivity)
    }

    @RunWith(JUnit4::class)
    class StandardTests : ConnectivityCollectorTests() {

        @Test
        fun factory_ReturnsModule_When_Enabled() {
            val context = mockk<TealiumContext>(relaxed = true)
            val network = mockk<NetworkUtilities>(relaxed = true)
            every { context.network } returns network
            every { network.connectionStatus } returns Observables.stateSubject(Status.Unknown)

            assertNotNull(
                ConnectivityCollector.Factory.create(
                    context, DataObject.EMPTY_OBJECT
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
    }

    @RunWith(Parameterized::class)
    class ConnectivityTypeTests(
        private val connectivityStatus: Status,
        private val expected: String
    ) : ConnectivityCollectorTests() {

        companion object {
            @JvmStatic
            @Parameterized.Parameters
            fun connectivityTypeParams() = arrayOf(
                arrayOf(Status.Connected(ConnectivityType.WIFI), "wifi"),
                arrayOf(Status.Connected(ConnectivityType.CELLULAR), "cellular"),
                arrayOf(Status.Connected(ConnectivityType.ETHERNET), "ethernet"),
                arrayOf(Status.Connected(ConnectivityType.UNKNOWN), "unknown"),
                arrayOf(Status.NotConnected, "none"),
                arrayOf(Status.Unknown, "unknown")
            )
        }

        @Test
        fun collect_ReturnsDataObject_Containing_ConnectivityType() {
            connectivity.onNext(connectivityStatus)
            val dispatchContext =
                DispatchContext(DispatchContext.Source.application(), DataObject.EMPTY_OBJECT)

            val dataObject = connectivityCollector.collect(dispatchContext)
            assertEquals(expected, dataObject.getString(Dispatch.Keys.CONNECTION_TYPE)!!)
        }
    }
}