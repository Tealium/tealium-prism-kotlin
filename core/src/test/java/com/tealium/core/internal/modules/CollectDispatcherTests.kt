package com.tealium.core.internal.modules

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.tealium.core.TealiumConfig
import com.tealium.core.api.Dispatch
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.HttpResponse
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.network.Success
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class CollectDispatcherTests {

    @RelaxedMockK
    lateinit var logger: Logger

    @MockK
    lateinit var networkHelper: NetworkHelper

    lateinit var collectDispatcher: CollectDispatcher

    private val account = "tealium_account"
    private val profile = "tealium_profile"

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        coEvery { networkHelper.post(any(), any()) } returns Success(
            HttpResponse(
                url = URL(CollectDispatcherSettings.DEFAULT_COLLECT_URL),
                statusCode = 200, message = "", headers = mapOf()
            )
        )
    }

    @Test
    fun dispatch_Individually_SendsJson_ToConfiguredEndpoint() = runTest {
        collectDispatcher = createCollectDispatcher()

        val dispatch = createTestDispatch("test")

        collectDispatcher.dispatch(listOf(dispatch)).test {
            assertEquals(dispatch.id, awaitItem().first().id)
            awaitComplete()
        }

        coVerify(timeout = 1000) {
            networkHelper.post(CollectDispatcherSettings.DEFAULT_COLLECT_URL, dispatch.payload())
        }
    }

    @Test
    fun dispatch_Individually_OverridesUrl_WhenUrlIsOverridden() = runTest {
        collectDispatcher = createCollectDispatcher(
            settings = CollectDispatcherSettings(url = "http://localhost/")
        )

        val dispatch = createTestDispatch("test")

        collectDispatcher.dispatch(listOf(dispatch)).test {
            assertEquals(dispatch.id, awaitItem().first().id)
            awaitComplete()
        }

        coVerify(timeout = 1000) {
            networkHelper.post("http://localhost/", any())
        }
    }

    @Test
    fun dispatch_Individually_OverridesProfile_WhenProfileIsOverridden() = runTest {
        collectDispatcher = createCollectDispatcher(
            settings = CollectDispatcherSettings(
                profile = "override"
            )
        )
        val dispatch = createTestDispatch("test", profile = "default")

        collectDispatcher.dispatch(listOf(dispatch)).test {
            assertEquals(dispatch.id, awaitItem().first().id)
            awaitComplete()
        }

        coVerify(timeout = 1000) {
            networkHelper.post(CollectDispatcherSettings.DEFAULT_COLLECT_URL, match {
                it.getString(Dispatch.Keys.TEALIUM_PROFILE) == "override"
            })
        }
    }

    @Test
    fun dispatch_Batches_SendsJson_ToConfiguredEndpoint() = runTest {
        collectDispatcher = createCollectDispatcher()

        val dispatch1 = createTestDispatch("test")
        val dispatch2 = createTestDispatch("test")

        collectDispatcher.dispatch(listOf(dispatch1, dispatch2)).test {
            val processed = awaitItem()
            assertEquals(dispatch1.id, processed[0].id)
            assertEquals(dispatch2.id, processed[1].id)
            awaitComplete()
        }

        coVerify(timeout = 1000) {
            networkHelper.post(CollectDispatcherSettings.DEFAULT_COLLECT_BATCH_URL, any())
        }
    }

    @Test
    fun dispatch_Batches_SendsIndividually_IfBatchOfOne() = runTest {
        collectDispatcher = createCollectDispatcher()

        val dispatch = createTestDispatch("test")

        collectDispatcher.dispatch(listOf(dispatch)).test {
            assertEquals(dispatch.id, awaitItem().first().id)
            awaitComplete()
        }

        coVerify(timeout = 1000) {
            networkHelper.post(CollectDispatcherSettings.DEFAULT_COLLECT_URL, dispatch.payload())
        }
    }

    @Test
    fun dispatch_Batches_OverridesUrl_WhenUrlIsOverridden() = runTest {
        collectDispatcher = createCollectDispatcher(
            settings = CollectDispatcherSettings(batchUrl = "http://localhost/")
        )

        val dispatch1 = createTestDispatch("test")
        val dispatch2 = createTestDispatch("test")

        collectDispatcher.dispatch(listOf(dispatch1, dispatch2)).test {
            val processed = awaitItem()
            assertEquals(dispatch1.id, processed[0].id)
            assertEquals(dispatch2.id, processed[1].id)
            awaitComplete()
        }

        coVerify(timeout = 1000) {
            networkHelper.post("http://localhost/", any())
        }
    }

    @Test
    fun dispatch_Batches_OverridesProfile_WhenProfileIsOverridden() = runTest {
        collectDispatcher = createCollectDispatcher(
            settings = CollectDispatcherSettings(
                profile = "override"
            )
        )
        val dispatch1 = createTestDispatch("test", profile = "default")
        val dispatch2 = createTestDispatch("test", profile = "default")

        collectDispatcher.dispatch(listOf(dispatch1, dispatch2)).test {
            val processed = awaitItem()
            assertEquals(dispatch1.id, processed[0].id)
            assertEquals(dispatch2.id, processed[1].id)
            awaitComplete()
        }

        coVerify(timeout = 1000) {
            networkHelper.post(CollectDispatcherSettings.DEFAULT_COLLECT_BATCH_URL, match {
                it.getBundle(CollectDispatcher.KEY_SHARED)!!
                    .getString(Dispatch.Keys.TEALIUM_PROFILE) == "override"
            })
        }
    }

    @Test
    fun dispatch_Batches_CompressesCommonKeys_And_LeavesUniqueKeys() = runTest {
        collectDispatcher = createCollectDispatcher()

        val dispatch1 = createTestDispatch("test", data = testBundle.copy {
            put("key_1", "string1")
            put("key_2", "string2")
        })
        val dispatch2 = createTestDispatch("test", data = testBundle.copy {
            put("key_3", "string3")
            put("key_4", "string4")
        })

        turbineScope {
            collectDispatcher.dispatch(listOf(dispatch1, dispatch2)).testIn(backgroundScope)
        }

        coVerify(timeout = 1000) {
            networkHelper.post(CollectDispatcherSettings.DEFAULT_COLLECT_BATCH_URL, match {
                val shared = it.getBundle(CollectDispatcher.KEY_SHARED)!!
                val events = it.getList(CollectDispatcher.KEY_EVENTS)!!
                val event1 = events.getBundle(0)!!
                val event2 = events.getBundle(1)!!

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
            })
        }
    }

    @Test
    fun dispatch_Batches_OverridesProfile_InSharedDataOnly() = runTest {
        collectDispatcher = createCollectDispatcher(
            settings = CollectDispatcherSettings(
                profile = "override"
            )
        )
        val dispatch1 = createTestDispatch("test", data = testBundle.copy {
            put("key_1", "string1")
            put("key_2", "string2")
        })
        val dispatch2 = createTestDispatch("test", data = testBundle.copy {
            put("key_3", "string3")
            put("key_4", "string4")
        })

        turbineScope {
            collectDispatcher.dispatch(listOf(dispatch1, dispatch2)).testIn(backgroundScope)
        }

        coVerify(timeout = 1000) {
            networkHelper.post(CollectDispatcherSettings.DEFAULT_COLLECT_BATCH_URL, match {
                val shared = it.getBundle(CollectDispatcher.KEY_SHARED)!!
                val events = it.getList(CollectDispatcher.KEY_EVENTS)!!
                val event1 = events.getBundle(0)!!
                val event2 = events.getBundle(1)!!

                shared.getString(Dispatch.Keys.TEALIUM_PROFILE) == "override"
                        && event1.get(Dispatch.Keys.TEALIUM_PROFILE) == null
                        && event2.get(Dispatch.Keys.TEALIUM_PROFILE) == null
            })
        }
    }

    @Test
    fun dispatch_Splits_WhenMultipleUniqueVisitorId() = runTest {
        collectDispatcher = createCollectDispatcher()

        val dispatch1 = createTestDispatch("test", visitorId = "visitor_1")
        val dispatch2 = createTestDispatch("test", visitorId = "visitor_2")

        collectDispatcher.dispatch(listOf(dispatch1, dispatch2)).test {
            val visitor1 = awaitItem()
            assertEquals(
                "visitor_1",
                visitor1.first().payload().getString(Dispatch.Keys.TEALIUM_VISITOR_ID)
            )

            val visitor2 = awaitItem()
            assertEquals(
                "visitor_2",
                visitor2.first().payload().getString(Dispatch.Keys.TEALIUM_VISITOR_ID)
            )

            awaitComplete()
        }

        coVerify(timeout = 1000) {
            networkHelper.post(CollectDispatcherSettings.DEFAULT_COLLECT_URL, match {
                it.getString(Dispatch.Keys.TEALIUM_VISITOR_ID) == "visitor_1"
            })
            networkHelper.post(CollectDispatcherSettings.DEFAULT_COLLECT_URL, match {
                it.getString(Dispatch.Keys.TEALIUM_VISITOR_ID) == "visitor_2"
            })
        }
    }

    @Test
    fun updateSettings_UpdatesIndividualUrl() = runTest {
        collectDispatcher = createCollectDispatcher()

        val dispatch1 = createTestDispatch("test")

        turbineScope {
            collectDispatcher.dispatch(listOf(dispatch1)).testIn(backgroundScope)
        }

        coVerify(timeout = 1000) {
            networkHelper.post(CollectDispatcherSettings.DEFAULT_COLLECT_URL, any())
        }

        val url = "https://localhost/"
        collectDispatcher.updateSettings(
            mockk(),
            ModuleSettingsImpl(
                enabled = true,
                mapOf(
                    CollectDispatcherSettings.KEY_COLLECT_URL to url
                )
            )
        )
        turbineScope {
            collectDispatcher.dispatch(listOf(dispatch1)).testIn(backgroundScope)
        }

        coVerify(timeout = 1000) {
            networkHelper.post(url, any())
        }
    }

    @Test
    fun updateSettings_UpdatesBatchUrl() = runTest {
        collectDispatcher = createCollectDispatcher()

        val dispatch1 = createTestDispatch("test")

        turbineScope {
            collectDispatcher.dispatch(listOf(dispatch1, dispatch1)).testIn(backgroundScope)
        }

        coVerify(timeout = 1000) {
            networkHelper.post(CollectDispatcherSettings.DEFAULT_COLLECT_BATCH_URL, any())
        }

        val url = "https://localhost/"
        collectDispatcher.updateSettings(
            mockk(),
            ModuleSettingsImpl(
                enabled = true,
                mapOf(
                    CollectDispatcherSettings.KEY_COLLECT_BATCH_URL to url
                )
            )
        )
        turbineScope {
            collectDispatcher.dispatch(listOf(dispatch1, dispatch1)).testIn(backgroundScope)
        }

        coVerify(timeout = 1000) {
            networkHelper.post(url, any())
        }
    }

    @Test
    fun updateSettings_UpdatesProfileOverride_ForIndividualEvents() = runTest {
        collectDispatcher = createCollectDispatcher(
            settings = CollectDispatcherSettings(
                profile = "default"
            )
        )

        val dispatch1 = createTestDispatch("test")

        turbineScope {
            collectDispatcher.dispatch(listOf(dispatch1)).testIn(backgroundScope)
        }

        coVerify(timeout = 1000) {
            networkHelper.post(CollectDispatcherSettings.DEFAULT_COLLECT_URL, match {
                it.getString(Dispatch.Keys.TEALIUM_PROFILE) == "default"
            })
        }

        val overrideProfile = "override"
        collectDispatcher.updateSettings(
            mockk(),
            ModuleSettingsImpl(
                enabled = true,
                mapOf(
                    CollectDispatcherSettings.KEY_COLLECT_PROFILE to overrideProfile
                )
            )
        )
        turbineScope {
            collectDispatcher.dispatch(listOf(dispatch1)).testIn(backgroundScope)
        }

        coVerify(timeout = 1000) {
            networkHelper.post(any(), match {
                it.getString(Dispatch.Keys.TEALIUM_PROFILE) == overrideProfile
            })
        }
    }


    @Test
    fun fromModuleSettings_PrefersUrlOverrides_ToDomainOverride() {
        val url = "https://localhost/"
        val settings = CollectDispatcherSettings.fromModuleSettings(
            ModuleSettingsImpl(
                enabled = true,
                mapOf(
                    CollectDispatcherSettings.KEY_COLLECT_URL to url,
                    CollectDispatcherSettings.KEY_COLLECT_BATCH_URL to url,
                    CollectDispatcherSettings.KEY_COLLECT_DOMAIN to "domain"
                )
            )
        )

        assertEquals(url, settings.url)
        assertEquals(url, settings.batchUrl)
    }

    @Test
    fun fromModuleSettings_UsesDomainOverride_WhenNoUrlOverride() {
        val settings = CollectDispatcherSettings.fromModuleSettings(
            ModuleSettingsImpl(
                enabled = true,
                mapOf(
                    CollectDispatcherSettings.KEY_COLLECT_DOMAIN to "domain"
                )
            )
        )

        assertEquals("https://domain/event", settings.url)
        assertEquals("https://domain/bulk-event", settings.batchUrl)
    }

    /**
     * Creates a new [CollectDispatcher] with the ioScope set to the [TestScope.backgroundScope]
     *
     * Reasonable defaults are used in case of parameter omission.
     */
    private fun createCollectDispatcher(
        settings: CollectDispatcherSettings = CollectDispatcherSettings(),
    ): CollectDispatcher {
        val config = mockk<TealiumConfig>()
        every { config.accountName } returns account
        every { config.profileName } returns profile

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
        data: TealiumBundle = TealiumBundle.EMPTY_BUNDLE
    ): Dispatch {
        return Dispatch.create(name, bundle = TealiumBundle.create {
            put(Dispatch.Keys.TEALIUM_PROFILE, profile)
            put(Dispatch.Keys.TEALIUM_VISITOR_ID, visitorId)
            putAll(data)
        })
    }

    /**
     * Bundle containing known sharable keys as both key and value
     */
    private val testBundle = TealiumBundle.create {
        put(Dispatch.Keys.TEALIUM_ACCOUNT, account)
        put(Dispatch.Keys.TEALIUM_PROFILE, profile)
    }
}