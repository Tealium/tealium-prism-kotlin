package com.tealium.core.internal.modules.collect

import com.tealium.core.api.Modules
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CollectModuleFactoryTests {

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
    fun create_Returns_CollectModule_When_ModuleSettings_Enabled() {
        val module = CollectModule.Factory()
            .create(Modules.Types.COLLECT, context, DataObject.EMPTY_OBJECT)

        assertNotNull(module)
        assertTrue(module is CollectModule)
    }

    @Test
    fun create_Returns_Null_When_Invalid_Url() {
        val factory = CollectModule.Factory()

        assertNull(factory.create(Modules.Types.COLLECT, mockk(), createConfigurationObject {
            it.setUrl("some_invalid_url")
        }))
    }

    @Test
    fun create_Returns_Null_When_Invalid_BatchUrl() {
        val factory = CollectModule.Factory()

        assertNull(factory.create(Modules.Types.COLLECT, mockk(), createConfigurationObject {
            it.setBatchUrl("some_invalid_url")
        }))
    }
}