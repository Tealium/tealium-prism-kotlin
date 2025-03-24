package com.tealium.core.api.settings

import com.tealium.core.internal.modules.consent.ConsentSettings
import com.tealium.core.internal.settings.ModuleSettings
import org.junit.Assert.*
import org.junit.Before

import org.junit.Test

class ConsentSettingsBuilderTests {

    private lateinit var builder: ConsentSettingsBuilder

    @Before
    fun setUp() {
        builder = ConsentSettingsBuilder()
    }

    @Test
    fun setDispatcherToPurposes_Sets_DispatcherPurposes_In_DataObject() {
        val consentSettings = builder.setDispatcherToPurposes(
            mapOf("dispatcher" to setOf("purpose_1", "purpose_2"))
        ).build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        val purposeMap = consentSettings.getDataObject(ConsentSettings.KEY_DISPATCHER_PURPOSES)!!
        val purposes = purposeMap.getDataList("dispatcher")!!

        assertEquals("purpose_1", purposes.getString(0))
        assertEquals("purpose_2", purposes.getString(1))
    }

    @Test
    fun setShouldRefireDispatchers_Sets_DispatcherPurposes_In_DataObject() {
        val consentSettings = builder.setShouldRefireDispatchers(
            setOf("dispatcher_1", "dispatcher_2")
        ).build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        val refireList = consentSettings.getDataList(ConsentSettings.KEY_REFIRE_DISPATCHERS)!!

        assertEquals("dispatcher_1", refireList.getString(0))
        assertEquals("dispatcher_2", refireList.getString(1))
    }
}