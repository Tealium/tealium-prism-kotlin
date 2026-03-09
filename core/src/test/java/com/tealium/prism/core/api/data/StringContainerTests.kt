package com.tealium.prism.core.api.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StringContainerTests {

    @Test
    fun constructor_Sets_Value_Correctly() {
        val testValue = "test_value"
        val stringContainer = StringContainer(testValue)

        assertEquals(testValue, stringContainer.value)
    }

    @Test
    fun asDataObject_Returns_Correct_DataObject() {
        val testValue = "test_value"
        val stringContainer = StringContainer(testValue)

        val dataObject = stringContainer.asDataObject()

        assertNotNull(dataObject)
        assertEquals(testValue, dataObject.getString(StringContainer.Converter.KEY_VALUE))
    }

    @Test
    fun converter_Converts_DataObject_To_StringContainer_Correctly() {
        val testValue = "test_value"
        val dataObject = DataObject.create {
            put(StringContainer.Converter.KEY_VALUE, testValue)
        }
        val stringContainer = StringContainer.Converter.convert(dataObject.asDataItem())

        assertNotNull(stringContainer)
        assertEquals(testValue, stringContainer?.value)
    }

    @Test
    fun converter_Returns_Null_When_DataObject_Is_Null() {
        val stringContainer = StringContainer.Converter.convert(DataItem.NULL)

        assertNull(stringContainer)
    }

    @Test
    fun converter_Returns_Null_When_Value_Is_Missing() {
        val stringContainer = StringContainer.Converter.convert(DataObject.EMPTY_OBJECT.asDataItem())

        assertNull(stringContainer)
    }

    @Test
    fun equals_And_HashCode_Are_Correct() {
        val value1 = "test_value"
        val value2 = "different_value"

        val container1 = StringContainer(value1)
        val container2 = StringContainer(value1)
        val container3 = StringContainer(value2)

        // Test equals
        assertEquals(container1, container2)
        assertNotEquals(container1, container3)

        // Test hashCode
        assertEquals(container1.hashCode(), container2.hashCode())
        assertNotEquals(container1.hashCode(), container3.hashCode())
    }
}