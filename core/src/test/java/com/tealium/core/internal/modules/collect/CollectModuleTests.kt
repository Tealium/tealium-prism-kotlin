package com.tealium.core.internal.modules.collect

import com.tealium.core.api.Modules
import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.network.HttpResponse
import com.tealium.core.api.network.NetworkCallback
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.NetworkResult.Success
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.core.api.tracking.Dispatch
import com.tealium.tests.common.SystemLogger
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class CollectModuleTests {

    @MockK
    lateinit var networkHelper: NetworkHelper

    @MockK
    lateinit var context: TealiumContext

    @MockK
    lateinit var config: TealiumConfig

    lateinit var collectModule: CollectModule

    private val logger: Logger = SystemLogger
    private val defaultConfiguration = CollectModuleConfiguration()
    private val localhost = URL("https://localhost/")
    private val account = "tealium_account"
    private val profile = "tealium_profile"

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { config.accountName } returns account
        every { config.profileName } returns profile
        every { context.config } returns config
        every { context.logger } returns logger

        val networking = mockk<NetworkUtilities>()
        every { networking.networkHelper } returns networkHelper
        every { context.network } returns networking

        val completionCapture = slot<NetworkCallback<NetworkResult>>()
        every { networkHelper.post(any<URL>(), any(), capture(completionCapture)) } answers {
            completionCapture.captured.onComplete(
                Success(
                    HttpResponse(
                        url = defaultConfiguration.url,
                        statusCode = 200, message = "", headers = mapOf()
                    )
                )
            )
            mockk(relaxed = true)
        }
    }

    @Test
    fun dispatch_Individually_SendsJson_ToConfiguredEndpoint() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkHelper.post(
                defaultConfiguration.url,
                dispatch.payload(),
                any()
            )
            observer(match {
                it.first().id == dispatch.id
            })
        }
    }

    @Test
    fun dispatch_Individually_OverridesUrl_WhenUrlIsOverridden() {
        collectModule = createCollectDispatcher(
            collectConfig = CollectModuleConfiguration(url = localhost)
        )
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkHelper.post(localhost, any(), any())
            observer(match {
                it.first().id == dispatch.id
            })
        }
    }

    @Test
    fun dispatch_Individually_OverridesProfile_WhenProfileIsOverridden() {
        collectModule = createCollectDispatcher(
            collectConfig = CollectModuleConfiguration(
                profile = "override"
            )
        )
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)
        val dispatch = createTestDispatch("test", profile = "default")

        collectModule.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultConfiguration.url, match {
                it.getString(Dispatch.Keys.TEALIUM_PROFILE) == "override"
            }, any())
            observer(match {
                it.first().id == dispatch.id
            })
        }
    }

    @Test
    fun dispatch_Batches_SendsJson_ToConfiguredEndpoint() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")
        val dispatch2 = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch1, dispatch2), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultConfiguration.batchUrl, any(), any())
            observer(match {
                it[0].id == dispatch1.id
                        && it[1].id == dispatch2.id
            })
        }
    }

    @Test
    fun dispatch_Batches_SendsIndividually_IfBatchOfOne() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkHelper.post(
                defaultConfiguration.url,
                dispatch.payload(),
                any()
            )
            observer(match {
                it.first().id == dispatch.id
            })
        }
    }

    @Test
    fun dispatch_Batches_OverridesUrl_WhenUrlIsOverridden() {
        collectModule = createCollectDispatcher(
            collectConfig = CollectModuleConfiguration(batchUrl = localhost)
        )
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")
        val dispatch2 = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch1, dispatch2), observer)

        verify(timeout = 1000) {
            networkHelper.post(localhost, any(), any())
            observer(match {
                it[0].id == dispatch1.id
                        && it[1].id == dispatch2.id
            })
        }
    }

    @Test
    fun dispatch_Batches_OverridesProfile_WhenProfileIsOverridden() {
        collectModule = createCollectDispatcher(
            collectConfig = CollectModuleConfiguration(
                profile = "override"
            )
        )
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)
        val dispatch1 = createTestDispatch("test", profile = "default")
        val dispatch2 = createTestDispatch("test", profile = "default")

        collectModule.dispatch(listOf(dispatch1, dispatch2), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultConfiguration.batchUrl, match {
                it.getDataObject(CollectModule.KEY_SHARED)!!
                    .getString(Dispatch.Keys.TEALIUM_PROFILE) == "override"
            }, any())
            observer(match {
                it[0].id == dispatch1.id
                        && it[1].id == dispatch2.id
            })
        }
    }

    @Test
    fun dispatch_Batches_CompressesCommonKeys_And_LeavesUniqueKeys() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test", data = testDataObject.copy {
            put("key_1", "string1")
            put("key_2", "string2")
        })
        val dispatch2 = createTestDispatch("test", data = testDataObject.copy {
            put("key_3", "string3")
            put("key_4", "string4")
        })

        collectModule.dispatch(listOf(dispatch1, dispatch2), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultConfiguration.batchUrl, match {
                val shared = it.getDataObject(CollectModule.KEY_SHARED)!!
                val events = it.getDataList(CollectModule.KEY_EVENTS)!!
                val event1 = events.getDataObject(0)!!
                val event2 = events.getDataObject(1)!!

                listOf(
                    Dispatch.Keys.TEALIUM_ACCOUNT,
                    Dispatch.Keys.TEALIUM_PROFILE
                ).fold(true) { acc, key ->
                    acc && shared.get(key)!!.value == key
                }
                        && event1.getString("key_1") == "string1"
                        && event1.getString("key_2") == "string2"
                        && event2.getString("key_3") == "string3"
                        && event2.getString("key_4") == "string4"
            }, any())
        }
    }

    @Test
    fun dispatch_Batches_OverridesProfile_InSharedDataOnly() {
        collectModule = createCollectDispatcher(
            collectConfig = CollectModuleConfiguration(
                profile = "override"
            )
        )
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)
        val dispatch1 = createTestDispatch("test", data = testDataObject.copy {
            put("key_1", "string1")
            put("key_2", "string2")
        })
        val dispatch2 = createTestDispatch("test", data = testDataObject.copy {
            put("key_3", "string3")
            put("key_4", "string4")
        })

        collectModule.dispatch(listOf(dispatch1, dispatch2), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultConfiguration.batchUrl, match {
                val shared = it.getDataObject(CollectModule.KEY_SHARED)!!
                val events = it.getDataList(CollectModule.KEY_EVENTS)!!
                val event1 = events.getDataObject(0)!!
                val event2 = events.getDataObject(1)!!

                shared.getString(Dispatch.Keys.TEALIUM_PROFILE) == "override"
                        && event1.get(Dispatch.Keys.TEALIUM_PROFILE) == null
                        && event2.get(Dispatch.Keys.TEALIUM_PROFILE) == null
            }, any())
        }
    }

    @Test
    fun dispatch_Splits_WhenMultipleUniqueVisitorId() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test", visitorId = "visitor_1")
        val dispatch2 = createTestDispatch("test", visitorId = "visitor_2")
        val dispatch3 = createTestDispatch("test2", visitorId = "visitor_2")

        collectModule.dispatch(listOf(dispatch1, dispatch2, dispatch3), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultConfiguration.url, match {
                it.getString(Dispatch.Keys.TEALIUM_VISITOR_ID) == "visitor_1"
            }, any())
            networkHelper.post(defaultConfiguration.batchUrl, match {
                it.getDataObject(CollectModule.KEY_SHARED)!!
                    .getString(Dispatch.Keys.TEALIUM_VISITOR_ID) == "visitor_2"
            }, any())

            observer(match { dispatches ->
                dispatches.first().payload()
                    .getString(Dispatch.Keys.TEALIUM_VISITOR_ID) == "visitor_1"
            })
            observer(match { dispatches ->
                dispatches[0].payload().getString(Dispatch.Keys.TEALIUM_VISITOR_ID) == "visitor_2"
                        && dispatches[1].payload()
                    .getString(Dispatch.Keys.TEALIUM_VISITOR_ID) == "visitor_2"
            })
        }
    }

    @Test
    fun updateConfiguration_UpdatesIndividualUrl() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch1), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultConfiguration.url, any(), any())
        }

        collectModule.updateConfiguration(
            createConfigurationObject { it.setUrl(localhost.toString()) }
        )
        collectModule.dispatch(listOf(dispatch1), observer)

        verify(timeout = 1000) {
            networkHelper.post(localhost, any(), any())
        }
    }

    @Test
    fun updateConfiguration_UpdatesBatchUrl() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch1, dispatch1), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultConfiguration.batchUrl, any(), any())
        }

        collectModule.updateConfiguration(
            createConfigurationObject { it.setBatchUrl(localhost.toString()) }
        )
        collectModule.dispatch(listOf(dispatch1, dispatch1), observer)

        verify(timeout = 1000) {
            networkHelper.post(localhost, any(), any())
        }
    }

    @Test
    fun updateConfiguration_UpdatesProfileOverride_ForIndividualEvents() {
        collectModule = createCollectDispatcher(
            collectConfig = CollectModuleConfiguration(
                profile = "default"
            )
        )
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch1), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultConfiguration.url, match {
                it.getString(Dispatch.Keys.TEALIUM_PROFILE) == "default"
            }, any())
        }

        val overrideProfile = "override"
        collectModule.updateConfiguration(
            createConfigurationObject { it.setProfile(overrideProfile) }
        )
        collectModule.dispatch(listOf(dispatch1), observer)

        verify(timeout = 1000) {
            networkHelper.post(any<URL>(), match {
                it.getString(Dispatch.Keys.TEALIUM_PROFILE) == overrideProfile
            }, any())
        }
    }

    @Test
    fun updateConfiguration_ReturnsSelf_When_Default_Configuration_Provided() {
        collectModule = createCollectDispatcher()

        assertSame(
            collectModule,
            collectModule.updateConfiguration(DataObject.EMPTY_OBJECT)
        )
    }

    @Test
    fun updateConfiguration_ReturnsNull_When_Invalid_Url() {
        collectModule = createCollectDispatcher()

        assertNull(collectModule.updateConfiguration(createConfigurationObject {
            it.setUrl("some_invalid_url")
        }))
    }

    @Test
    fun updateConfiguration_ReturnsNull_When_Invalid_BatchUrl() {
        collectModule = createCollectDispatcher()

        assertNull(collectModule.updateConfiguration(createConfigurationObject {
            it.setBatchUrl("some_invalid_url")
        }))
    }

    @Test
    fun name_Matches_Factory_Name() {
        collectModule = createCollectDispatcher()

        assertEquals(CollectModule.Factory().moduleType, collectModule.id)
    }

    /**
     * Creates a new [CollectModule] with reasonable defaults in case of parameter omission.
     */
    private fun createCollectDispatcher(
        collectConfig: CollectModuleConfiguration = CollectModuleConfiguration(),
    ): CollectModule {
        return CollectModule(
            Modules.Types.COLLECT,
            config,
            logger,
            networkHelper,
            collectConfig,
        )
    }

    /**
     * Creates a new [Dispatch] with the supplied event name and data.
     * All dispatches returned are for the same visitor id and profile unless overridden in the [data]
     * object
     */
    private fun createTestDispatch(
        name: String,
        visitorId: String = "visitor",
        profile: String = "default",
        data: DataObject = DataObject.EMPTY_OBJECT
    ): Dispatch {
        return Dispatch.create(name, dataObject = DataObject.create {
            put(Dispatch.Keys.TEALIUM_PROFILE, profile)
            put(Dispatch.Keys.TEALIUM_VISITOR_ID, visitorId)
            putAll(data)
        })
    }

    /**
     * [DataObject] containing known sharable keys as both key and value
     */
    private val testDataObject = DataObject.create {
        put(Dispatch.Keys.TEALIUM_ACCOUNT, account)
        put(Dispatch.Keys.TEALIUM_PROFILE, profile)
    }
}