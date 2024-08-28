package com.tealium.core.api

import com.tealium.core.internal.modules.ConnectivityCollector
import com.tealium.core.internal.modules.VisitorServiceImpl
import com.tealium.core.internal.modules.collect.CollectDispatcher
import com.tealium.core.internal.modules.consent.ConsentModule
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModulesTests {

    @Test
    fun consent_Returns_ConsentModuleFactory_With_No_Enforced_Settings() {
        val consentFactory = Modules.consent(mockk())

        assertTrue(consentFactory is ConsentModule.Factory)
        assertNull(consentFactory.getEnforcedSettings())
    }

    @Test
    fun consent_WithConfig_Returns_ConsentModuleFactory_With_Enforced_Settings() {
        val consentFactory = Modules.consent(mockk()) { settings ->
            settings.setDispatcherToPurposes(mapOf())
        }

        assertTrue(consentFactory is ConsentModule.Factory)
        assertNotNull(consentFactory.getEnforcedSettings())
    }

    @Test
    fun collect_Returns_CollectModuleFactory_With_No_Enforced_Settings() {
        val collectFactory = Modules.collect()

        assertTrue(collectFactory is CollectDispatcher.Factory)
        assertNull(collectFactory.getEnforcedSettings())
    }

    @Test
    fun collect_WithConfig_Returns_CollectModuleFactory_With_Enforced_Settings() {
        val collectFactory = Modules.collect { settings ->
            settings.setProfile("test")
        }

        assertTrue(collectFactory is CollectDispatcher.Factory)
        assertNotNull(collectFactory.getEnforcedSettings())
    }

    @Test
    fun visitorService_Returns_VisitorServiceFactory_With_No_Enforced_Settings() {
        val visitorServiceFactory = Modules.visitorService()

        assertTrue(visitorServiceFactory is VisitorServiceImpl.Factory)
        assertNull(visitorServiceFactory.getEnforcedSettings())
    }

    @Test
    fun visitorService_WithConfig_Returns_VisitorServiceFactory_With_Enforced_Settings() {
        val visitorServiceFactory = Modules.visitorService { settings ->
            settings.setProfile("test")
        }

        assertTrue(visitorServiceFactory is VisitorServiceImpl.Factory)
        assertNotNull(visitorServiceFactory.getEnforcedSettings())
    }

    @Test
    fun connectivityCollector_Returns_ConnectivityCollectorFactory_With_No_Enforced_Settings() {
        val connectivityCollectorFactory = Modules.connectivityCollector()

        assertTrue(connectivityCollectorFactory is ConnectivityCollector.Factory)
        assertNull(connectivityCollectorFactory.getEnforcedSettings())
    }
}