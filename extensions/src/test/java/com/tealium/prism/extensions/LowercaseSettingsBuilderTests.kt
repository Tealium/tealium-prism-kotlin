package com.tealium.prism.extensions

import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.extensions.internal.lowercase.LowercasePolicy
import com.tealium.prism.extensions.api.lowercase.LowercaseSettingsBuilder
import com.tealium.prism.extensions.internal.lowercase.LowercaseConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LowercaseSettingsBuilderTests {

    @Test
    fun build_NoPolicySet_ReturnsNullConfig() {
        val settings = LowercaseSettingsBuilder("test_id").build()
        val config = LowercaseConfiguration.Converter.convert(settings.configuration.asDataItem())

        assertNull(config)
    }

    @Test
    fun build_lowercaseAllVariables_ReturnsAllVariablesPolicy() {
        val settings = LowercaseSettingsBuilder("test_id")
            .lowercaseAllVariables()
            .build()
        val config = LowercaseConfiguration.Converter.convert(settings.configuration.asDataItem())

        assertTrue(config?.policy is LowercasePolicy.AllVariables)
    }

    @Test
    fun build_AddSingleVariable_ReturnsInputsPolicyWithOneVariable() {
        val ref = ReferenceContainer.key("my_key")
        val settings = LowercaseSettingsBuilder("test_id")
            .lowercaseVariables(listOf(ref))
            .build()
        val config = LowercaseConfiguration.Converter.convert(settings.configuration.asDataItem())

        assertTrue(config?.policy is LowercasePolicy.Variables)
        val variables = (config?.policy as? LowercasePolicy.Variables)?.references
        assertEquals(1, variables?.size)
        assertEquals(ref, variables?.first())
    }

    @Test
    fun build_AddMultipleVariables_ReturnsInputsPolicyWithAllVariables() {
        val ref1 = ReferenceContainer.key("key1")
        val ref2 = ReferenceContainer.key("key2")
        val settings = LowercaseSettingsBuilder("test_id")
            .lowercaseVariables(listOf(ref1, ref2))
            .build()
        val config = LowercaseConfiguration.Converter.convert(settings.configuration.asDataItem())

        assertTrue(config?.policy is LowercasePolicy.Variables)
        val variables = (config?.policy as? LowercasePolicy.Variables)?.references
        assertEquals(2, variables?.size)
        assertTrue(variables?.contains(ref1) == true)
        assertTrue(variables?.contains(ref2) == true)
    }

    @Test
    fun build_LowercaseEmptyVariablesList_CreatesEmptyInputsPolicy() {
        val settings = LowercaseSettingsBuilder("test_id")
            .lowercaseVariables(emptyList())
            .build()
        val config = LowercaseConfiguration.Converter.convert(settings.configuration.asDataItem())

        assertTrue(config?.policy is LowercasePolicy.Variables)
        val variables = (config?.policy as? LowercasePolicy.Variables)?.references
        assertTrue(variables?.isEmpty() == true)
    }

    @Test
    fun build_SetsTransformationId() {
        val settings = LowercaseSettingsBuilder("my_transformation_id")
            .lowercaseAllVariables()
            .build()
        assertEquals("my_transformation_id", settings.id)
    }

    @Test
    fun build_LowercaseVariablesThenAllVariables_ReplacesPolicy() {
        val ref = ReferenceContainer.key("my_key")
        val settings = LowercaseSettingsBuilder("test_id")
            .lowercaseVariables(listOf(ref))
            .lowercaseAllVariables()
            .build()
        val config = LowercaseConfiguration.Converter.convert(settings.configuration.asDataItem())

        assertTrue(config?.policy is LowercasePolicy.AllVariables)
    }

    @Test
    fun build_LowercaseAllVariablesThenVariables_ReplacesPolicy() {
        val ref = ReferenceContainer.key("my_key")
        val settings = LowercaseSettingsBuilder("test_id")
            .lowercaseAllVariables()
            .lowercaseVariables(listOf(ref))
            .build()
        val config = LowercaseConfiguration.Converter.convert(settings.configuration.asDataItem())

        assertTrue(config?.policy is LowercasePolicy.Variables)
        val variables = (config?.policy as? LowercasePolicy.Variables)?.references
        assertEquals(1, variables?.size)
        assertEquals(ref, variables?.first())
    }
}
