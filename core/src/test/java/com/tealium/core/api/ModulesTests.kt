package com.tealium.core.api

import com.tealium.core.internal.modules.ConnectivityDataModule
import com.tealium.core.internal.modules.collect.CollectModule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModulesTests {

    @Test
    fun collect_Returns_CollectModuleFactory_With_No_Enforced_Settings() {
        val collectFactory = Modules.collect()

        assertTrue(collectFactory is CollectModule.Factory)
        assertNull(collectFactory.getEnforcedSettings())
    }

    @Test
    fun collect_WithConfig_Returns_CollectModuleFactory_With_Enforced_Settings() {
        val collectFactory = Modules.collect { settings ->
            settings.setProfile("test")
        }

        assertTrue(collectFactory is CollectModule.Factory)
        assertNotNull(collectFactory.getEnforcedSettings())
    }

    @Test
    fun connectivityData_Returns_ConnectivityDataFactory_With_No_Enforced_Settings() {
        val connectivityDataFactory = Modules.connectivityData()

        assertTrue(connectivityDataFactory is ConnectivityDataModule.Factory)
        assertNull(connectivityDataFactory.getEnforcedSettings())
    }
}