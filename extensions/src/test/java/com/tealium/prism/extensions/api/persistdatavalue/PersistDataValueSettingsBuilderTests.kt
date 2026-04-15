package com.tealium.prism.extensions.api.persistdatavalue

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueSource
import com.tealium.prism.core.api.misc.ExpiryPolicy
import com.tealium.prism.extensions.internal.persistdatavalue.PersistDataValueConfiguration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersistDataValueSettingsBuilderTests {
    private val input = "input_string".asDataItem()
    private val destination = ReferenceContainer.key("destination_key")
    private val converter = PersistDataValueConfiguration.Converter

    @Test
    fun persist_WithReferenceInput_BuildsCorrectConfiguration() {
        val input = ReferenceContainer.key("input_key")
        val builder = PersistDataValueSettingsBuilder("pdv_test")
            .persistFrom(input, destination)
        val settings = builder.build()
        val configurationDataObject = settings.configuration

        val configuration = converter.convert(configurationDataObject.asDataItem())
        assertEquals("input_key", (configuration?.input as? ValueSource.Reference)?.reference?.path.toString())
        assertEquals("destination_key", (configuration?.destination)?.path.toString())
        assertEquals(ExpiryPolicy.SESSION, configuration?.expiryPolicy)
        assertEquals(UpdatePolicy.ALLOW_UPDATE, configuration?.updatePolicy)
    }

    @Test
    fun persist_WithStringInput_BuildsCorrectConfiguration() {
        val builder = PersistDataValueSettingsBuilder("pdv_test")
            .persistConstant(input, destination)
        val settings = builder.build()
        val configurationDataObject = settings.configuration
        val configuration = converter.convert(configurationDataObject.asDataItem())

        assertEquals("input_string".asDataItem(), (configuration?.input as? ValueSource.Constant)?.value?.value)
        assertEquals("destination_key", (configuration?.destination)?.path.toString())
        assertEquals(ExpiryPolicy.SESSION, configuration?.expiryPolicy)
        assertEquals(UpdatePolicy.ALLOW_UPDATE, configuration?.updatePolicy)
    }

    @Test
    fun persist_SetExpiryPolicy() {
        val builder = PersistDataValueSettingsBuilder("pdv_test")
            .persistConstant(input, destination)
            .setExpiryPolicy(ExpiryPolicy.FOREVER)
        val settings = builder.build()
        val configurationDataObject = settings.configuration
        val configuration = converter.convert(configurationDataObject.asDataItem())

        assertEquals(ExpiryPolicy.FOREVER, configuration?.expiryPolicy)
    }

    @Test
    fun persist_SetUpdatePolicy() {
        val builder = PersistDataValueSettingsBuilder("pdv_test")
            .persistConstant(input, destination)
            .setUpdatePolicy(UpdatePolicy.KEEP_FIRST_VALUE)
        val settings = builder.build()
        val configurationDataObject = settings.configuration
        val configuration = converter.convert(configurationDataObject.asDataItem())

        assertEquals(UpdatePolicy.KEEP_FIRST_VALUE, configuration?.updatePolicy)
    }

    @Test
    fun persist_Defaults_AreSet() {
        val builder = PersistDataValueSettingsBuilder("pdv_test")
            .persistConstant(input, destination)
        val settings = builder.build()
        val configurationDataObject = settings.configuration
        val configuration = converter.convert(configurationDataObject.asDataItem())

        assertEquals(ExpiryPolicy.SESSION, configuration?.expiryPolicy)
        assertEquals(UpdatePolicy.ALLOW_UPDATE, configuration?.updatePolicy)
    }

    @Test
    fun build_WithoutPersist_ReturnsNullConfiguration() {
        val builder = PersistDataValueSettingsBuilder("pdv_test")
        val settings = builder.build()
        val configurationDataObject = settings.configuration
        val configuration = converter.convert(configurationDataObject.asDataItem())

        assertNull(configuration?.input)
        assertNull(configuration?.destination)
    }
}