package com.tealium.prism.extensions.internal.lowercase

import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LowercaseConfigurationTests {

    @Test
    fun converter_WithAllVariablesString_ReturnsAllVariablesPolicy() {
        val dataObject = DataObject.create {
            put(LowercaseConfiguration.Converter.KEY_VARIABLES, "allvariables")
        }
        val config = LowercaseConfiguration.Converter.convert(dataObject.asDataItem())

        assertTrue(config?.policy is LowercasePolicy.AllVariables)
    }

    @Test
    fun converter_WithSingleInputList_ReturnsInputsPolicy() {
        val ref = ReferenceContainer.key("test_key")
        val dataObject = DataObject.create {
            put(LowercaseConfiguration.Converter.KEY_VARIABLES, listOf(ref).asDataList())
        }
        val config = LowercaseConfiguration.Converter.convert(dataObject.asDataItem())

        assertTrue(config?.policy is LowercasePolicy.Variables)
        val variables = (config?.policy as? LowercasePolicy.Variables)?.references
        assertEquals(1, variables?.size)
        assertEquals(ref, variables?.first())
    }

    @Test
    fun converter_WithMultipleInputsList_ReturnsInputsPolicy() {
        val ref1 = ReferenceContainer.key("key1")
        val ref2 = ReferenceContainer.key("key2")
        val dataObject = DataObject.create {
            put(LowercaseConfiguration.Converter.KEY_VARIABLES, listOf(ref1, ref2).asDataList())
        }
        val config = LowercaseConfiguration.Converter.convert(dataObject.asDataItem())

        assertTrue(config?.policy is LowercasePolicy.Variables)
        val variables = (config?.policy as? LowercasePolicy.Variables)?.references
        assertEquals(2, variables?.size)
        assertTrue(variables?.contains(ref1) == true)
        assertTrue(variables?.contains(ref2) == true)
    }

    @Test
    fun converter_WithEmptyInputsList_CreatesEmptyInputsPolicy() {
        val dataObject = DataObject.create {
            put(LowercaseConfiguration.Converter.KEY_VARIABLES, emptyList<ReferenceContainer>().asDataList())
        }
        val config = LowercaseConfiguration.Converter.convert(dataObject.asDataItem())

        assertTrue(config?.policy is LowercasePolicy.Variables)
        val variables = (config?.policy as? LowercasePolicy.Variables)?.references
        assertTrue(variables?.isEmpty() == true)
    }

    @Test
    fun converter_WithEmptyDataObject_ReturnsNull() {
        val config = LowercaseConfiguration.Converter.convert(DataObject.EMPTY_OBJECT.asDataItem())
        assertNull(config)
    }

    @Test
    fun converter_WithMissingVariablesKey_ReturnsNull() {
        val dataObject = DataObject.create {
            put("other_key", "some_value")
        }
        val config = LowercaseConfiguration.Converter.convert(dataObject.asDataItem())
        assertNull(config)
    }

    @Test
    fun converter_WithInvalidVariablesType_ReturnsNull() {
        val dataObject = DataObject.create {
            put(LowercaseConfiguration.Converter.KEY_VARIABLES, 123)
        }
        val config = LowercaseConfiguration.Converter.convert(dataObject.asDataItem())
        assertNull(config)
    }
}
