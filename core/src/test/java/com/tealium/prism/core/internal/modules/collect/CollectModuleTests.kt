package com.tealium.prism.core.internal.modules.collect

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.HttpResponse
import com.tealium.prism.core.api.network.NetworkClient
import com.tealium.prism.core.api.network.NetworkException
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Success
import com.tealium.prism.core.api.network.NetworkUtilities
import com.tealium.prism.core.internal.pubsub.CompletedDisposable
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.tests.common.SystemLogger
import io.mockk.MockKAnnotations
import io.mockk.MockKMatcherScope
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
import java.net.MalformedURLException
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class CollectModuleTests {

    @MockK
    lateinit var networkClient: NetworkClient

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
        every { networking.networkClient } returns networkClient
        every { context.network } returns networking

        val completionCapture = slot<Callback<NetworkResult>>()
        every {
            networkClient.sendRequest(any<HttpRequest.Builder>(), capture(completionCapture))
        } answers {
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
            networkClient.sendRequest(matchRequest { request ->
                request.url == defaultConfiguration.url
                        && request.body == dispatch.payload().toString()
            }, any())
            observer(match {
                it.first().id == dispatch.id
            })
        }
    }

    @Test
    fun dispatch_Sends_UnbuiltRequest_So_NetworkClient_Owns_The_Build() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        collectModule.dispatch(listOf(createTestDispatch("test")), observer)

        // The Builder overload is the one that absorbs MalformedURLException, so passing an
        // unbuilt request is what guarantees the observer is notified for any url.
        verify(timeout = 1000, exactly = 1) {
            networkClient.sendRequest(any<HttpRequest.Builder>(), any())
        }
        verify(exactly = 0) {
            networkClient.sendRequest(any<HttpRequest>(), any())
        }
    }

    @Test
    fun dispatch_Notifies_Observer_When_Request_Fails_To_Build() {
        // The client owns the build, so a url that cannot build still completes with a Failure
        val completionCapture = slot<Callback<NetworkResult>>()
        every {
            networkClient.sendRequest(any<HttpRequest.Builder>(), capture(completionCapture))
        } answers {
            completionCapture.captured.onComplete(
                NetworkResult.Failure(NetworkException.UnexpectedException(MalformedURLException()))
            )
            CompletedDisposable
        }

        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)
        val dispatch = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            observer(match { it.first().id == dispatch.id })
        }
    }

    @Test
    fun dispatch_Individually_GzipsPayload() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest { it.isGzip }, any())
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
            networkClient.sendRequest(matchRequest { it.url == localhost }, any())
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
            networkClient.sendRequest(matchRequest { request ->
                request.url == defaultConfiguration.url
                        && request.bodyAsDataObject()
                    .getString(Dispatch.Keys.TEALIUM_PROFILE) == "override"
            }, any())
            observer(match {
                it.first().id == dispatch.id
            })
        }
    }

    @Test
    fun dispatch_Individually_UsesDefaultUrl_WhenTraceIdNotInPayload() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest {
                it.url.toString() == defaultConfiguration.url.toString() &&
                        !it.url.toString().contains("tealium_trace_id")
            }, any())
        }
    }

    @Test
    fun dispatch_Individually_AppendsUrl_WhenTraceIdInPayload() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch = createTestDispatch("test", data = testDataObject.copy {
            put("tealium_trace_id", "12345")
        })

        collectModule.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest {
                it.url.toString().contains("tealium_trace_id=12345")
            }, any())
        }
    }

    @Test
    fun dispatch_Individually_DoesNotAppendUrl_WhenTraceIdIsEmpty() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch = createTestDispatch("test", data = testDataObject.copy {
            put("tealium_trace_id", "")
        })

        collectModule.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest {
                !it.url.toString().contains("tealium_trace_id=")
            }, any())
        }
    }

    @Test
    fun dispatch_Individually_AppendsUrlWithTraceId_WhenUrlHasExistingParameters() {
        collectModule = createCollectDispatcher(
            collectConfig = CollectModuleConfiguration(
                url = URL("https://collect.tealiumiq.com/event?existing_param=value")
            )
        )
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch = createTestDispatch("test", data = testDataObject.copy {
            put("tealium_trace_id", "12345")
        })

        collectModule.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest {
                it.url.toString().contains("tealium_trace_id=12345") &&
                        it.url.toString().contains("existing_param=value")
            }, any())
        }
    }

    @Test
    fun dispatch_Batch_UsesBatchUrl_WhenTraceIdNotInPayload() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")
        val dispatch2 = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch1, dispatch2), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest {
                it.url.toString() == defaultConfiguration.batchUrl.toString() &&
                        !it.url.toString().contains("tealium_trace_id")
            }, any())
        }
    }

    @Test
    fun dispatch_Batch_AppendsUrl_WhenTraceIdInPayload() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")
        val dispatch2 = createTestDispatch("test", data = testDataObject.copy {
            put("tealium_trace_id", "12345")
        })

        collectModule.dispatch(listOf(dispatch1, dispatch2), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest {
                it.url.toString().contains("tealium_trace_id=12345")
            }, any())
        }
    }

    @Test
    fun dispatch_Batch_AppendsUrl_WithFirstTraceIdInPayload() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")
        val dispatch2 = createTestDispatch("test", data = testDataObject.copy {
            put("tealium_trace_id", "12345")
        })
        val dispatch3 = createTestDispatch("test", data = testDataObject.copy {
            put("tealium_trace_id", "67890")
        })

        collectModule.dispatch(listOf(dispatch1, dispatch2, dispatch3), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest {
                it.url.toString().contains("tealium_trace_id=12345") &&
                        !it.url.toString().contains("tealium_trace_id=67890")
            }, any())
        }
    }

    @Test
    fun dispatch_Batch_DoesNotAppendUrl_WhenTraceIdIsEmpty() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")
        val dispatch2 = createTestDispatch("test", data = testDataObject.copy {
            put("tealium_trace_id", "")
        })

        collectModule.dispatch(listOf(dispatch1, dispatch2), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest {
                !it.url.toString().contains("tealium_trace_id=")
            }, any())
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
            networkClient.sendRequest(
                matchRequest { it.url == defaultConfiguration.batchUrl },
                any()
            )
            observer(match {
                it[0].id == dispatch1.id
                        && it[1].id == dispatch2.id
            })
        }
    }

    @Test
    fun dispatch_Batches_GzipsPayload() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")
        val dispatch2 = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch1, dispatch2), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest { it.isGzip }, any())
        }
    }

    @Test
    fun dispatch_Batches_SendsIndividually_IfBatchOfOne() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest { request ->
                request.url == defaultConfiguration.url
                        && request.body == dispatch.payload().toString()
            }, any())
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
            networkClient.sendRequest(matchRequest { it.url == localhost }, any())
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
            networkClient.sendRequest(matchRequest { request ->
                request.url == defaultConfiguration.batchUrl
                        && request.bodyAsDataObject()
                    .getDataObject(CollectModule.KEY_SHARED)!!
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
            networkClient.sendRequest(matchRequest { request ->
                if (request.url != defaultConfiguration.batchUrl) return@matchRequest false

                val payload = request.bodyAsDataObject()
                val shared = payload.getDataObject(CollectModule.KEY_SHARED)!!
                val events = payload.getDataList(CollectModule.KEY_EVENTS)!!
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
            networkClient.sendRequest(matchRequest { request ->
                if (request.url != defaultConfiguration.batchUrl) return@matchRequest false

                val payload = request.bodyAsDataObject()
                val shared = payload.getDataObject(CollectModule.KEY_SHARED)!!
                val events = payload.getDataList(CollectModule.KEY_EVENTS)!!
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
            networkClient.sendRequest(matchRequest { request ->
                request.url == defaultConfiguration.url
                        && request.bodyAsDataObject()
                    .getString(Dispatch.Keys.TEALIUM_VISITOR_ID) == "visitor_1"
            }, any())
            networkClient.sendRequest(matchRequest { request ->
                request.url == defaultConfiguration.batchUrl
                        && request.bodyAsDataObject()
                    .getDataObject(CollectModule.KEY_SHARED)!!
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
            networkClient.sendRequest(
                matchRequest { it.url == defaultConfiguration.url },
                any()
            )
        }

        collectModule.updateConfiguration(
            createConfigurationObject { it.setUrl(localhost.toString()) }
        )
        collectModule.dispatch(listOf(dispatch1), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest { it.url == localhost }, any())
        }
    }

    @Test
    fun updateConfiguration_UpdatesBatchUrl() {
        collectModule = createCollectDispatcher()
        val observer: (List<Dispatch>) -> Unit = mockk(relaxed = true)

        val dispatch1 = createTestDispatch("test")

        collectModule.dispatch(listOf(dispatch1, dispatch1), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(
                matchRequest { it.url == defaultConfiguration.batchUrl },
                any()
            )
        }

        collectModule.updateConfiguration(
            createConfigurationObject { it.setBatchUrl(localhost.toString()) }
        )
        collectModule.dispatch(listOf(dispatch1, dispatch1), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest { it.url == localhost }, any())
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
            networkClient.sendRequest(matchRequest { request ->
                request.url == defaultConfiguration.url
                        && request.bodyAsDataObject()
                    .getString(Dispatch.Keys.TEALIUM_PROFILE) == "default"
            }, any())
        }

        val overrideProfile = "override"
        collectModule.updateConfiguration(
            createConfigurationObject { it.setProfile(overrideProfile) }
        )
        collectModule.dispatch(listOf(dispatch1), observer)

        verify(timeout = 1000) {
            networkClient.sendRequest(matchRequest { request ->
                request.bodyAsDataObject()
                    .getString(Dispatch.Keys.TEALIUM_PROFILE) == overrideProfile
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
     * Matches the [HttpRequest.Builder] overload of [NetworkClient.sendRequest] - which is what
     * [CollectModule] delegates to, so that the client owns the build - by applying [predicate]
     * to the [HttpRequest] the builder produces.
     */
    private fun MockKMatcherScope.matchRequest(
        predicate: (HttpRequest) -> Boolean
    ): HttpRequest.Builder = match { predicate(it.build()) }

    /**
     * Parses the JSON [HttpRequest.body] back into a [DataObject] so that assertions can be made
     * against the payload structurally rather than against raw JSON text.
     *
     * Returns [DataObject.EMPTY_OBJECT] if the body is missing or unparseable, so that a bad
     * payload surfaces as a failed match rather than an exception inside a `match` block.
     */
    private fun HttpRequest.bodyAsDataObject(): DataObject =
        body?.let(DataObject::fromString) ?: DataObject.EMPTY_OBJECT

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
            networkClient,
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
