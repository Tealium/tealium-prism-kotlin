package com.tealium.prism.extensions.internal.setdatavalues

import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueSource
import com.tealium.prism.extensions.internal.setdatavalues.SetDataValuesConfiguration.Converter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetDataValuesConfigurationTests {

    @Test
    fun converter_WithValidDataItem_ReturnsConfiguration() {
        val destination = ReferenceContainer.key("destination_key")
        val operation =
            SetDataValuesOperation(
                ValueSource.Reference(ReferenceContainer.key("test_key")),
                destination
            )
        val configDataObj = DataObject.create {
            put(Converter.KEY_OPERATIONS, listOf(operation).asDataList())
        }
        val config = Converter.convert(configDataObj.asDataItem())
        assertEquals(1, config!!.operations.size)
        assertEquals("destination_key", config.operations[0].destination.path.toString())
        assertEquals(
            "test_key",
            (config.operations[0].input as ValueSource.Reference).reference.path.toString()
        )
    }

    @Test
    fun converter_WithEmptyOperations_ReturnsConfigurationWithEmptyList() {
        val configDataObj = DataObject.create {
            put(Converter.KEY_OPERATIONS, emptyList<SetDataValuesOperation>().asDataList())
        }
        val config = Converter.convert(configDataObj.asDataItem())
        assertNotNull(config)
        assertTrue(config!!.operations.isEmpty())
    }

    @Test
    fun converter_WithNullOperations_ReturnsNull() {
        val configDataObj = DataObject.EMPTY_OBJECT
        val config = Converter.convert(configDataObj.asDataItem())
        assertNull(config)
    }
}