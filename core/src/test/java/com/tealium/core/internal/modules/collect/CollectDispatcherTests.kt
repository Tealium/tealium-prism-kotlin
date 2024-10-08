package com.tealium.core.internal.modules.collect

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
class CollectDispatcherTests {

    @MockK
    lateinit var networkHelper: NetworkHelper

    @MockK
    lateinit var context: TealiumContext

    @MockK
    lateinit var config: TealiumConfig

    lateinit var collectDispatcher: CollectDispatcher

    private val logger: Logger = SystemLogger
    private val defaultSettings = CollectDispatcherSettings()
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
                        url = defaultSettings.url,
                        statusCode = 200, message = "", headers = mapOf()
                    )
                )
            )
            mockk(relaxed = true)
        }
    }

    @Test
    fun dispatch_Individually_SendsJson_ToConfiguredEndpoint() {
        collectDispatcher = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch = createTestDispatch("test")

        collectDispatcher.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkHelper.post(
                defaultSettings.url,
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
        collectDispatcher = createCollectDispatcher(
            settings = CollectDispatcherSettings(url = localhost)
        )
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch = createTestDispatch("test")

        collectDispatcher.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkHelper.post(localhost, any(), any())
            observer(match {
                it.first().id == dispatch.id
            })
        }
    }

    @Test
    fun dispatch_Individually_OverridesProfile_WhenProfileIsOverridden() {
        collectDispatcher = createCollectDispatcher(
            settings = CollectDispatcherSettings(
                profile = "override"
            )
        )
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)
        val dispatch = createTestDispatch("test", profile = "default")

        collectDispatcher.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultSettings.url, match {
                it.getString(Dispatch.Keys.TEALIUM_PROFILE) == "override"
            }, any())
            observer(match {
                it.first().id == dispatch.id
            })
        }
    }

    @Test
    fun dispatch_Batches_SendsJson_ToConfiguredEndpoint() {
        collectDispatcher = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")
        val dispatch2 = createTestDispatch("test")

        collectDispatcher.dispatch(listOf(dispatch1, dispatch2), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultSettings.batchUrl, any(), any())
            observer(match {
                it[0].id == dispatch1.id
                        && it[1].id == dispatch2.id
            })
        }
    }

    @Test
    fun dispatch_Batches_SendsIndividually_IfBatchOfOne() {
        collectDispatcher = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch = createTestDispatch("test")

        collectDispatcher.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkHelper.post(
                defaultSettings.url,
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
        collectDispatcher = createCollectDispatcher(
            settings = CollectDispatcherSettings(batchUrl = localhost)
        )
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")
        val dispatch2 = createTestDispatch("test")

        collectDispatcher.dispatch(listOf(dispatch1, dispatch2), observer)

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
        collectDispatcher = createCollectDispatcher(
            settings = CollectDispatcherSettings(
                profile = "override"
            )
        )
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)
        val dispatch1 = createTestDispatch("test", profile = "default")
        val dispatch2 = createTestDispatch("test", profile = "default")

        collectDispatcher.dispatch(listOf(dispatch1, dispatch2), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultSettings.batchUrl, match {
                it.getDataObject(CollectDispatcher.KEY_SHARED)!!
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
        collectDispatcher = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test", data = testDataObject.copy {
            put("key_1", "string1")
            put("key_2", "string2")
        })
        val dispatch2 = createTestDispatch("test", data = testDataObject.copy {
            put("key_3", "string3")
            put("key_4", "string4")
        })

        collectDispatcher.dispatch(listOf(dispatch1, dispatch2), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultSettings.batchUrl, match {
                val shared = it.getDataObject(CollectDispatcher.KEY_SHARED)!!
                val events = it.getDataList(CollectDispatcher.KEY_EVENTS)!!
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
        collectDispatcher = createCollectDispatcher(
            settings = CollectDispatcherSettings(
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

        collectDispatcher.dispatch(listOf(dispatch1, dispatch2), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultSettings.batchUrl, match {
                val shared = it.getDataObject(CollectDispatcher.KEY_SHARED)!!
                val events = it.getDataList(CollectDispatcher.KEY_EVENTS)!!
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
        collectDispatcher = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test", visitorId = "visitor_1")
        val dispatch2 = createTestDispatch("test", visitorId = "visitor_2")
        val dispatch3 = createTestDispatch("test2", visitorId = "visitor_2")

        collectDispatcher.dispatch(listOf(dispatch1, dispatch2, dispatch3), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultSettings.url, match {
                it.getString(Dispatch.Keys.TEALIUM_VISITOR_ID) == "visitor_1"
            }, any())
            networkHelper.post(defaultSettings.batchUrl, match {
                it.getDataObject(CollectDispatcher.KEY_SHARED)!!
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
    fun updateSettings_UpdatesIndividualUrl() {
        collectDispatcher = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")

        collectDispatcher.dispatch(listOf(dispatch1), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultSettings.url, any(), any())
        }

        collectDispatcher.updateSettings(
            createSettings { it.setUrl(localhost.toString()) }
        )
        collectDispatcher.dispatch(listOf(dispatch1), observer)

        verify(timeout = 1000) {
            networkHelper.post(localhost, any(), any())
        }
    }

    @Test
    fun updateSettings_UpdatesBatchUrl() {
        collectDispatcher = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")

        collectDispatcher.dispatch(listOf(dispatch1, dispatch1), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultSettings.batchUrl, any(), any())
        }

        collectDispatcher.updateSettings(
            createSettings { it.setBatchUrl(localhost.toString()) }
        )
        collectDispatcher.dispatch(listOf(dispatch1, dispatch1), observer)

        verify(timeout = 1000) {
            networkHelper.post(localhost, any(), any())
        }
    }

    @Test
    fun updateSettings_UpdatesProfileOverride_ForIndividualEvents() {
        collectDispatcher = createCollectDispatcher(
            settings = CollectDispatcherSettings(
                profile = "default"
            )
        )
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")

        collectDispatcher.dispatch(listOf(dispatch1), observer)

        verify(timeout = 1000) {
            networkHelper.post(defaultSettings.url, match {
                it.getString(Dispatch.Keys.TEALIUM_PROFILE) == "default"
            }, any())
        }

        val overrideProfile = "override"
        collectDispatcher.updateSettings(
            createSettings { it.setProfile(overrideProfile) }
        )
        collectDispatcher.dispatch(listOf(dispatch1), observer)

        verify(timeout = 1000) {
            networkHelper.post(any<URL>(), match {
                it.getString(Dispatch.Keys.TEALIUM_PROFILE) == overrideProfile
            }, any())
        }
    }

    @Test
    fun updateSettings_ReturnsSelf_When_ModuleSettings_Enabled() {
        collectDispatcher = createCollectDispatcher()

        assertSame(
            collectDispatcher,
            collectDispatcher.updateSettings(DataObject.EMPTY_OBJECT)
        )
    }

    @Test
    fun updateSettings_ReturnsNull_When_Invalid_Url() {
        collectDispatcher = createCollectDispatcher()

        assertNull(collectDispatcher.updateSettings(createSettings {
            it.setUrl("some_invalid_url")
        }))
    }

    @Test
    fun updateSettings_ReturnsNull_When_Invalid_BatchUrl() {
        collectDispatcher = createCollectDispatcher()

        assertNull(collectDispatcher.updateSettings(createSettings {
            it.setBatchUrl("some_invalid_url")
        }))
    }

    @Test
    fun name_Matches_Factory_Name() {
        collectDispatcher = createCollectDispatcher()

        assertEquals(CollectDispatcher.Factory().id, collectDispatcher.id)
    }

    /**
     * Creates a new [CollectDispatcher] with the ioScope set to the [TestScope.backgroundScope]
     *
     * Reasonable defaults are used in case of parameter omission.
     */
    private fun createCollectDispatcher(
        settings: CollectDispatcherSettings = CollectDispatcherSettings(),
    ): CollectDispatcher {
        return CollectDispatcher(
            config,
            logger,
            networkHelper,
            settings,
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