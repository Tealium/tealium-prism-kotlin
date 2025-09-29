package com.tealium.core.internal.modules

import com.tealium.core.api.Modules
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AppDataModuleTests {

    @RelaxedMockK
    private lateinit var appDataProvider: AppDataProvider

    private lateinit var appDataModule: AppDataModule

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { appDataProvider.appUuid } returns "123456789"
        every { appDataProvider.appRdns } returns "com.tealium.test"
        every { appDataProvider.appName } returns "mobile"
        every { appDataProvider.appBuild } returns "1"
        every { appDataProvider.appVersion } returns "1.0.0"
        every { appDataProvider.appMemoryUsage } returns 50L

        appDataModule = AppDataModule(appDataProvider)
    }

    @Test
    fun id_Returns_AppData() {
        assertEquals(Modules.Types.APP_DATA, AppDataModule.Factory.moduleType)
    }

    @Test
    fun collect_Returns_AppData() {
        val dispatchContext =
            DispatchContext(DispatchContext.Source.application(), DataObject.EMPTY_OBJECT)
        val bundle = appDataModule.collect(dispatchContext)

        assertEquals("123456789", bundle.getString(Dispatch.Keys.APP_UUID))
        assertEquals("com.tealium.test", bundle.getString(Dispatch.Keys.APP_RDNS))
        assertEquals("mobile", bundle.getString(Dispatch.Keys.APP_NAME))
        assertEquals("1", bundle.getString(Dispatch.Keys.APP_BUILD))
        assertEquals("1.0.0", bundle.getString(Dispatch.Keys.APP_VERSION))
        assertEquals(50L, bundle.getLong(Dispatch.Keys.APP_MEMORY_USAGE))
    }
}