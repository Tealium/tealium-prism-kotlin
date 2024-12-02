package com.tealium.core.internal.modules.consent

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.consent.ConsentManagementAdapter
import com.tealium.core.api.pubsub.Observables
import com.tealium.tests.common.SystemLogger
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ConsentModuleFactoryTests {

    @RelaxedMockK
    private lateinit var cmp: ConsentManagementAdapter

    @MockK
    private lateinit var context: TealiumContext

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { context.logger } returns SystemLogger
        every { context.transformerRegistry } returns mockk(relaxed = true)
    }

    @Test
    fun create_Returns_Null_When_Neither_QueueManager_Or_Modules_Are_Set() {
        val consentFactory = ConsentModule.Factory(cmp)

        assertNull(consentFactory.create(context, DataObject.EMPTY_OBJECT))
    }

    @Test
    fun create_Returns_Null_When_Only_QueueManager_Set() {
        val consentFactory = ConsentModule.Factory(cmp).copy(
            queueManager = mockk()
        )

        assertNull(consentFactory.create(context, DataObject.EMPTY_OBJECT))
    }

    @Test
    fun create_Returns_Null_When_Only_Modules_Are_Set() {
        val consentFactory = ConsentModule.Factory(cmp).copy(
            modules = Observables.stateSubject(setOf())
        )

        assertNull(consentFactory.create(context, DataObject.EMPTY_OBJECT))
    }

    @Test
    fun create_Returns_ConsentModule_When_QueueManager_And_Modules_Are_Set() {
        val consentFactory = ConsentModule.Factory(cmp).copy(
            queueManager = mockk(),
            modules = Observables.stateSubject(setOf())
        )

        assertNotNull(consentFactory.create(context, DataObject.EMPTY_OBJECT))
    }

    @Test
    fun getEnforcedSettings_Returns_Provided_Settings_Param() {
        val settings = DataObject.create {
            put("some_Setting", "some_value")
        }
        val consentFactory = ConsentModule.Factory(cmp, settings = settings)

        assertEquals(settings, consentFactory.getEnforcedSettings())
    }
}