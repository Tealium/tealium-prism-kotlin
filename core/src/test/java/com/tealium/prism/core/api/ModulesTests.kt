package com.tealium.prism.core.api

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.settings.modules.CollectSettingsBuilder
import com.tealium.prism.core.api.settings.modules.ConnectivityDataSettingsBuilder
import com.tealium.prism.core.internal.modules.ConnectivityDataModule
import com.tealium.prism.core.internal.modules.collect.CollectModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModulesTests {

    @Test
    fun collect_With_Null_Returns_CollectModuleFactory_With_No_Enforced_Settings() {
        val collectFactory = Modules.collect(null)

        assertTrue(collectFactory is CollectModule.Factory)
        assertEquals(emptyList<DataObject>(), collectFactory.getEnforcedSettings())
    }

    @Test
    fun collect_Without_Block_Returns_CollectModuleFactory_With_Default_Enforced_Settings() {
        val collectFactory = Modules.collect()

        assertTrue(collectFactory is CollectModule.Factory)
        val factorySettings = collectFactory.getEnforcedSettings().first()
        assertNotNull(factorySettings)
        assertEquals(CollectSettingsBuilder().build(), factorySettings)
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
    fun collect_WithMultipleConfig_Returns_CollectModuleFactory_With_Multiple_Enforced_Settings() {
        val collectFactory = Modules.collect({ settings ->
            settings.setProfile("test")
        }, { settings -> settings })

        assertTrue(collectFactory is CollectModule.Factory)
        val enforcedSettings = collectFactory.getEnforcedSettings()
        assertEquals(2, enforcedSettings.size)
    }

    @Test
    fun collect_Returns_Factory_That_Supports_Multiple_Instances() {
        val collectFactory = Modules.collect()

        assertTrue(collectFactory.allowsMultipleInstances)
    }

    @Test
    fun connectivityData_With_Null_Returns_ConnectivityDataFactory_With_No_Enforced_Settings() {
        val connectivityDataFactory = Modules.connectivityData(null)

        assertTrue(connectivityDataFactory is ConnectivityDataModule.Factory)
        assertEquals(emptyList<DataObject>(), connectivityDataFactory.getEnforcedSettings())
    }

    @Test
    fun connectivityData_Without_Block_Returns_ConnectivityDataFactory_With_Default_Enforced_Settings() {
        val connectivityDataFactory = Modules.connectivityData()

        assertTrue(connectivityDataFactory is ConnectivityDataModule.Factory)
        val factorySettings = connectivityDataFactory.getEnforcedSettings().first()
        assertNotNull(factorySettings)
        assertEquals(ConnectivityDataSettingsBuilder().build(), factorySettings)
    }
}