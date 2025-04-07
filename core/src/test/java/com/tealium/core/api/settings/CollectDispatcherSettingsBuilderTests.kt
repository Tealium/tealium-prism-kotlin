package com.tealium.core.api.settings

import com.tealium.core.internal.modules.collect.CollectDispatcherConfiguration
import com.tealium.core.internal.settings.ModuleSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CollectDispatcherSettingsBuilderTests {

    private lateinit var builder: CollectDispatcherSettingsBuilder

    @Before
    fun setUp() {
        builder = CollectDispatcherSettingsBuilder()
    }

    @Test
    fun setUrl_Sets_Url_In_DataObject() {
        val config = builder.setUrl("test-url").build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals(
            "test-url",
            config.getString(CollectDispatcherConfiguration.KEY_COLLECT_URL)
        )
    }

    @Test
    fun setBatchUrl_Sets_BatchUrl_In_DataObject() {
        val config = builder.setBatchUrl("test-url").build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals(
            "test-url",
            config.getString(CollectDispatcherConfiguration.KEY_COLLECT_BATCH_URL)
        )
    }

    @Test
    fun setProfile_Sets_Profile_In_DataObject() {
        val config = builder.setProfile("profile").build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals(
            "profile",
            config.getString(CollectDispatcherConfiguration.KEY_COLLECT_PROFILE)
        )
    }

    @Test
    fun setDomain_Sets_Domain_In_DataObject() {
        val config = builder.setDomain("domain").build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals(
            "domain",
            config.getString(CollectDispatcherConfiguration.KEY_COLLECT_DOMAIN)
        )
    }
}