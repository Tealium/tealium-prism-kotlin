package com.tealium.core.internal.modules.collect

import com.tealium.core.api.TealiumConfig
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.network.NetworkUtilities
import com.tealium.tests.common.SystemLogger
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class CollectDispatcherFactoryTests {

    @MockK
    lateinit var context: TealiumContext
    @MockK
    lateinit var config: TealiumConfig
    @MockK
    lateinit var networkHelper: NetworkHelper

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { config.accountName } returns "test"
        every { config.profileName } returns "test"
        every { context.config } returns config
        every { context.logger } returns SystemLogger

        val networking = mockk<NetworkUtilities>()
        every { networking.networkHelper } returns networkHelper
        every { context.network } returns networking
    }

    @Test
    fun create_ReturnsCollectDispatcher_When_ModuleSettings_Enabled() {
        val module = CollectDispatcher.Factory()
            .create(context, DataObject.EMPTY_OBJECT)

        Assert.assertNotNull(module)
        Assert.assertTrue(module is CollectDispatcher)
    }

    @Test
    fun create_ReturnsNull_When_Invalid_Url() {
        val factory = CollectDispatcher.Factory()

        Assert.assertNull(factory.create(mockk(), createSettings {
            it.setUrl("some_invalid_url")
        }))
    }

    @Test
    fun create_ReturnsNull_When_Invalid_BatchUrl() {
        val factory = CollectDispatcher.Factory()

        Assert.assertNull(factory.create(mockk(), createSettings {
            it.setBatchUrl("some_invalid_url")
        }))
    }
}