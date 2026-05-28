package com.tealium.prism.extensions.api.persistdatavalue

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueSource
import com.tealium.prism.core.api.misc.ExpiryPolicy
import com.tealium.tests.common.configuration
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
        val settings = PersistDataValueSettingsBuilder("pdv_test")
            .persistFrom(input, destination)

        val configuration = converter.convert(settings.configuration.asDataItem())
        assertEquals("input_key", (configuration?.input as? ValueSource.Reference)?.reference?.path.toString())
        assertEquals("destination_key", (configuration?.destination)?.path.toString())
        assertEquals(ExpiryPolicy.SESSION, configuration?.expiryPolicy)
        assertEquals(UpdatePolicy.ALLOW_UPDATE, configuration?.updatePolicy)
    }

    @Test
    fun persist_WithStringInput_BuildsCorrectConfiguration() {
        val settings = PersistDataValueSettingsBuilder("pdv_test")
            .persistConstant(input, destination)
        val configuration = converter.convert(settings.configuration.asDataItem())

        assertEquals("input_string".asDataItem(), (configuration?.input as? ValueSource.Constant)?.value?.value)
        assertEquals("destination_key", (configuration?.destination)?.path.toString())
        assertEquals(ExpiryPolicy.SESSION, configuration?.expiryPolicy)
        assertEquals(UpdatePolicy.ALLOW_UPDATE, configuration?.updatePolicy)
    }

    @Test
    fun persist_SetExpiryPolicy() {
        val settings = PersistDataValueSettingsBuilder("pdv_test")
            .persistConstant(input, destination)
            .setExpiryPolicy(ExpiryPolicy.FOREVER)
        val configuration = converter.convert(settings.configuration.asDataItem())

        assertEquals(ExpiryPolicy.FOREVER, configuration?.expiryPolicy)
    }

    @Test
    fun persist_SetUpdatePolicy() {
        val settings = PersistDataValueSettingsBuilder("pdv_test")
            .persistConstant(input, destination)
            .setUpdatePolicy(UpdatePolicy.KEEP_FIRST_VALUE)
        val configuration = converter.convert(settings.configuration.asDataItem())

        assertEquals(UpdatePolicy.KEEP_FIRST_VALUE, configuration?.updatePolicy)
    }

    @Test
    fun persist_Defaults_AreSet() {
        val settings = PersistDataValueSettingsBuilder("pdv_test")
            .persistConstant(input, destination)
        val configuration = converter.convert(settings.configuration.asDataItem())

        assertEquals(ExpiryPolicy.SESSION, configuration?.expiryPolicy)
        assertEquals(UpdatePolicy.ALLOW_UPDATE, configuration?.updatePolicy)
    }

    @Test
    fun build_WithoutPersist_ReturnsNullConfiguration() {
        val settings = PersistDataValueSettingsBuilder("pdv_test")
        val configuration = converter.convert(settings.configuration.asDataItem())

        assertNull(configuration?.input)
        assertNull(configuration?.destination)
    }
}