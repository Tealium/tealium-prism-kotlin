package com.tealium.prism.core.api.settings

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValueContainerTests {

    @Test
    fun constructor_Sets_Value_Correctly() {
        val testValue = "test_value"
        val valueContainer = ValueContainer(testValue)

        assertEquals(testValue, valueContainer.value)
    }

    @Test
    fun asDataObject_Returns_Correct_DataObject() {
        val testValue = "test_value"
        val valueContainer = ValueContainer(testValue)

        val dataObject = valueContainer.asDataObject()

        assertNotNull(dataObject)
        assertEquals(testValue, dataObject.getString(ValueContainer.Converter.KEY_VALUE))
    }

    @Test
    fun converter_Converts_DataObject_To_ValueContainer_Correctly() {
        val testValue = "test_value"
        val dataObject = DataObject.create {
            put(ValueContainer.Converter.KEY_VALUE, testValue)
        }
        val valueContainer = ValueContainer.Converter.convert(dataObject.asDataItem())

        assertNotNull(valueContainer)
        assertEquals(testValue, valueContainer?.value)
    }

    @Test
    fun converter_Returns_Null_When_DataObject_Is_Null() {
        val valueContainer = ValueContainer.Converter.convert(DataItem.NULL)

        assertNull(valueContainer)
    }

    @Test
    fun converter_Returns_Null_When_Value_Is_Missing() {
        val valueContainer = ValueContainer.Converter.convert(DataObject.EMPTY_OBJECT.asDataItem())

        assertNull(valueContainer)
    }

    @Test
    fun equals_And_HashCode_Are_Correct() {
        val value1 = "test_value"
        val value2 = "different_value"

        val container1 = ValueContainer(value1)
        val container2 = ValueContainer(value1)
        val container3 = ValueContainer(value2)

        // Test equals
        assertEquals(container1, container2)
        assertNotEquals(container1, container3)

        // Test hashCode
        assertEquals(container1.hashCode(), container2.hashCode())
        assertNotEquals(container1.hashCode(), container3.hashCode())
    }
}
