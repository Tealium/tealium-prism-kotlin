package com.tealium.core.api.settings

import com.tealium.core.internal.modules.collect.CollectDispatcherSettings
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
        val collectSettings = builder.setUrl("test-url").build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals(
            "test-url",
            collectSettings.getString(CollectDispatcherSettings.KEY_COLLECT_URL)
        )
    }

    @Test
    fun setBatchUrl_Sets_BatchUrl_In_DataObject() {
        val collectSettings = builder.setBatchUrl("test-url").build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals(
            "test-url",
            collectSettings.getString(CollectDispatcherSettings.KEY_COLLECT_BATCH_URL)
        )
    }

    @Test
    fun setProfile_Sets_Profile_In_DataObject() {
        val collectSettings = builder.setProfile("profile").build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals(
            "profile",
            collectSettings.getString(CollectDispatcherSettings.KEY_COLLECT_PROFILE)
        )
    }

    @Test
    fun setDomain_Sets_Domain_In_DataObject() {
        val collectSettings = builder.setDomain("domain").build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals(
            "domain",
            collectSettings.getString(CollectDispatcherSettings.KEY_COLLECT_DOMAIN)
        )
    }
}