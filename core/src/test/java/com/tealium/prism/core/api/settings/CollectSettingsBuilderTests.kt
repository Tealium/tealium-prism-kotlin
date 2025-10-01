package com.tealium.prism.core.api.settings

import com.tealium.prism.core.api.settings.modules.CollectSettingsBuilder
import com.tealium.prism.core.internal.modules.collect.CollectModuleConfiguration
import com.tealium.prism.core.internal.settings.ModuleSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CollectSettingsBuilderTests {

    private lateinit var builder: CollectSettingsBuilder

    @Before
    fun setUp() {
        builder = CollectSettingsBuilder()
    }

    @Test
    fun setUrl_Sets_Url_In_DataObject() {
        val config = builder.setUrl("test-url").build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals(
            "test-url",
            config.getString(CollectModuleConfiguration.KEY_COLLECT_URL)
        )
    }

    @Test
    fun setBatchUrl_Sets_BatchUrl_In_DataObject() {
        val config = builder.setBatchUrl("test-url").build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals(
            "test-url",
            config.getString(CollectModuleConfiguration.KEY_COLLECT_BATCH_URL)
        )
    }

    @Test
    fun setProfile_Sets_Profile_In_DataObject() {
        val config = builder.setProfile("profile").build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals(
            "profile",
            config.getString(CollectModuleConfiguration.KEY_COLLECT_PROFILE)
        )
    }

    @Test
    fun setDomain_Sets_Domain_In_DataObject() {
        val config = builder.setDomain("domain").build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals(
            "domain",
            config.getString(CollectModuleConfiguration.KEY_COLLECT_DOMAIN)
        )
    }
}