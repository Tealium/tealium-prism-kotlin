package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataItemUtils.asDataObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValueContainerTests {

    @Test
    fun constructor_Sets_String_Value_Correctly() {
        val testValue = "test_value".asDataItem()
        val valueContainer = ValueContainer(testValue)

        assertEquals(testValue, valueContainer.value)
    }

    @Test
    fun constructor_Sets_Int_Value_Correctly() {
        val testValue = 1.asDataItem()
        val valueContainer = ValueContainer(testValue)

        assertEquals(testValue, valueContainer.value)
    }

    @Test
    fun constructor_Sets_Double_Value_Correctly() {
        val testValue = 1.1.asDataItem()
        val valueContainer = ValueContainer(testValue)

        assertEquals(testValue, valueContainer.value)
    }

    @Test
    fun constructor_Sets_Long_Value_Correctly() {
        val testValue = 100L.asDataItem()
        val valueContainer = ValueContainer(testValue)

        assertEquals(testValue, valueContainer.value)
    }

    @Test
    fun constructor_Sets_Boolean_Value_Correctly() {
        val testValue = true.asDataItem()
        val valueContainer = ValueContainer(testValue)

        assertEquals(testValue, valueContainer.value)
    }

    @Test
    fun constructor_Sets_DataList_Value_Correctly() {
        val testValue = listOf(1, 2, 3).asDataList()
            .asDataItem()
        val valueContainer = ValueContainer(testValue)

        assertEquals(testValue, valueContainer.value)
    }

    @Test
    fun constructor_Sets_DataObject_Value_Correctly() {
        val testValue = mapOf("string" to "string").asDataObject()
            .asDataItem()
        val valueContainer = ValueContainer(testValue)

        assertEquals(testValue, valueContainer.value)
    }

    @Test
    fun asDataObject_Returns_DataObject_Containing_String_Value() {
        val testValue = "test_value".asDataItem()
        val valueContainer = ValueContainer(testValue)

        val dataObject = valueContainer.asDataObject()

        assertNotNull(dataObject)
        assertEquals(testValue, dataObject.get(ValueContainer.Converter.KEY_VALUE))
    }

    @Test
    fun asDataObject_Returns_DataObject_Containing_Int_Value() {
        val testValue = 1.asDataItem()
        val valueContainer = ValueContainer(testValue)

        val dataObject = valueContainer.asDataObject()

        assertNotNull(dataObject)
        assertEquals(testValue, dataObject.get(ValueContainer.Converter.KEY_VALUE))
    }

    @Test
    fun asDataObject_Returns_DataObject_Containing_Double_Value() {
        val testValue = 1.1.asDataItem()
        val valueContainer = ValueContainer(testValue)

        val dataObject = valueContainer.asDataObject()

        assertNotNull(dataObject)
        assertEquals(testValue, dataObject.get(ValueContainer.Converter.KEY_VALUE))
    }

    @Test
    fun asDataObject_Returns_DataObject_Containing_Long_Value() {
        val testValue = 100L.asDataItem()
        val valueContainer = ValueContainer(testValue)

        val dataObject = valueContainer.asDataObject()

        assertNotNull(dataObject)
        assertEquals(testValue, dataObject.get(ValueContainer.Converter.KEY_VALUE))
    }

    @Test
    fun asDataObject_Returns_DataObject_Containing_Boolean_Value() {
        val testValue = false.asDataItem()
        val valueContainer = ValueContainer(testValue)

        val dataObject = valueContainer.asDataObject()

        assertNotNull(dataObject)
        assertEquals(testValue, dataObject.get(ValueContainer.Converter.KEY_VALUE))
    }

    @Test
    fun asDataObject_Returns_DataObject_Containing_DataList_Value() {
        val testValue = listOf(1, 2, 3).asDataList()
            .asDataItem()
        val valueContainer = ValueContainer(testValue)

        val dataObject = valueContainer.asDataObject()

        assertNotNull(dataObject)
        assertEquals(testValue, dataObject.get(ValueContainer.Converter.KEY_VALUE))
    }

    @Test
    fun asDataObject_Returns_DataObject_Containing_DataObject_Value() {
        val testValue = mapOf("string" to "string").asDataObject()
            .asDataItem()
        val valueContainer = ValueContainer(testValue)

        val dataObject = valueContainer.asDataObject()

        assertNotNull(dataObject)
        assertEquals(testValue, dataObject.get(ValueContainer.Converter.KEY_VALUE))
    }

    @Test
    fun converter_Converts_DataObject_To_ValueContainer_With_String_Correctly() {
        val testValue = "test_value".asDataItem()
        val dataObject = DataObject.create {
            put(ValueContainer.Converter.KEY_VALUE, testValue)
        }
        val valueContainer = ValueContainer.Converter.convert(dataObject.asDataItem())

        assertNotNull(valueContainer)
        assertEquals(testValue, valueContainer?.value)
    }

    @Test
    fun converter_Converts_DataObject_To_ValueContainer_With_Int_Correctly() {
        val testValue = 1.asDataItem()
        val dataObject = DataObject.create {
            put(ValueContainer.Converter.KEY_VALUE, testValue)
        }
        val valueContainer = ValueContainer.Converter.convert(dataObject.asDataItem())

        assertNotNull(valueContainer)
        assertEquals(testValue, valueContainer?.value)
    }

    @Test
    fun converter_Converts_DataObject_To_ValueContainer_With_Double_Correctly() {
        val testValue = 1.1.asDataItem()
        val dataObject = DataObject.create {
            put(ValueContainer.Converter.KEY_VALUE, testValue)
        }
        val valueContainer = ValueContainer.Converter.convert(dataObject.asDataItem())

        assertNotNull(valueContainer)
        assertEquals(testValue, valueContainer?.value)
    }

    @Test
    fun converter_Converts_DataObject_To_ValueContainer_With_Long_Correctly() {
        val testValue = 100L.asDataItem()
        val dataObject = DataObject.create {
            put(ValueContainer.Converter.KEY_VALUE, testValue)
        }
        val valueContainer = ValueContainer.Converter.convert(dataObject.asDataItem())

        assertNotNull(valueContainer)
        assertEquals(testValue, valueContainer?.value)
    }

    @Test
    fun converter_Converts_DataObject_To_ValueContainer_With_Boolean_Correctly() {
        val testValue = false.asDataItem()
        val dataObject = DataObject.create {
            put(ValueContainer.Converter.KEY_VALUE, testValue)
        }
        val valueContainer = ValueContainer.Converter.convert(dataObject.asDataItem())

        assertNotNull(valueContainer)
        assertEquals(testValue, valueContainer?.value)
    }

    @Test
    fun converter_Converts_DataObject_To_ValueContainer_With_DataList_Correctly() {
        val testValue = listOf(1, 2, 3).asDataList()
            .asDataItem()
        val dataObject = DataObject.create {
            put(ValueContainer.Converter.KEY_VALUE, testValue)
        }
        val valueContainer = ValueContainer.Converter.convert(dataObject.asDataItem())

        assertNotNull(valueContainer)
        assertEquals(testValue, valueContainer?.value)
    }

    @Test
    fun converter_Converts_DataObject_To_ValueContainer_With_DataObject_Correctly() {
        val testValue = mapOf("string" to "string").asDataObject()
            .asDataItem()
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
        val value1 = "test_value".asDataItem()
        val value2 = "different_value".asDataItem()

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