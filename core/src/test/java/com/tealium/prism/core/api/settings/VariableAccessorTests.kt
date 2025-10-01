package com.tealium.prism.core.api.settings

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import org.junit.Assert.*
import org.junit.Test

class VariableAccessorTests {

    @Test
    fun constructor_Sets_Properties_Correctly() {
        val variable = "test_variable"
        val path = listOf("path1", "path2")

        val accessor = VariableAccessor(variable, path)

        assertEquals(variable, accessor.variable)
        assertEquals(path, accessor.path)
    }

    @Test
    fun constructor_Sets_Null_Path() {
        val variable = "test_variable"

        val accessor = VariableAccessor(variable, null)

        assertEquals(variable, accessor.variable)
        assertNull(accessor.path)
    }

    @Test
    fun asDataObject_Returns_Correct_DataObject_With_Path() {
        val variable = "test_variable"
        val path = listOf("path1", "path2")
        val accessor = VariableAccessor(variable, path)

        val dataObject = accessor.asDataObject()

        assertNotNull(dataObject)
        val valueContainer =
            dataObject.get(VariableAccessor.Converter.KEY_VARIABLE, ValueContainer.Converter)
        assertEquals(variable, valueContainer?.value)

        val pathList = dataObject.getDataList(VariableAccessor.Converter.KEY_PATH)
        assertNotNull(pathList)
        val pathItems = pathList?.mapNotNull { it.getString() }
        assertEquals(path, pathItems)
    }

    @Test
    fun asDataObject_Returns_Correct_DataObject_Without_Path() {
        val variable = "test_variable"
        val accessor = VariableAccessor(variable, null)

        val dataObject = accessor.asDataObject()

        assertNotNull(dataObject)
        val valueContainer =
            dataObject.get(VariableAccessor.Converter.KEY_VARIABLE, ValueContainer.Converter)
        assertEquals(variable, valueContainer?.value)

        assertNull(dataObject.getDataList(VariableAccessor.Converter.KEY_PATH))
    }

    @Test
    fun converter_Converts_DataObject_To_VariableAccessor_Correctly_With_Path() {
        val variable = "test_variable"
        val path = listOf("path1", "path2")

        val dataObject = DataObject.create {
            put(VariableAccessor.Converter.KEY_VARIABLE, ValueContainer(variable))
            put(VariableAccessor.Converter.KEY_PATH, DataList.create {
                add("path1")
                add("path2")
            })
        }

        val accessor = VariableAccessor.Converter.convert(dataObject.asDataItem())!!

        assertEquals(variable, accessor.variable)
        assertEquals(path, accessor.path)
    }

    @Test
    fun converter_Converts_DataObject_To_VariableAccessor_Correctly_Without_Path() {
        val variable = "test_variable"

        val dataObject = DataObject.create {
            put(VariableAccessor.Converter.KEY_VARIABLE, ValueContainer(variable))
        }

        val accessor = VariableAccessor.Converter.convert(dataObject.asDataItem())

        assertNotNull(accessor)
        assertEquals(variable, accessor?.variable)
        assertNull(accessor?.path)
    }

    @Test
    fun converter_Returns_Null_When_DataObject_Is_Null() {
        val accessor = VariableAccessor.Converter.convert(DataItem.NULL)

        assertNull(accessor)
    }

    @Test
    fun converter_Returns_Null_When_Variable_Is_Missing() {
        val accessor = VariableAccessor.Converter.convert(DataObject.EMPTY_OBJECT.asDataItem())

        assertNull(accessor)
    }

    @Test
    fun converter_Returns_Null_When_Variable_Is_Improperly_Formatted() {
        val accessor = VariableAccessor.Converter.convert(DataObject.create {
            put(VariableAccessor.Converter.KEY_VARIABLE, "value")
        }.asDataItem())

        assertNull(accessor)
    }

    @Test
    fun equals_And_HashCode_Are_Correct() {
        val variable1 = "test_variable"
        val variable2 = "different_variable"
        val path1 = listOf("path1", "path2")
        val path2 = listOf("path3", "path4")

        val accessor1 = VariableAccessor(variable1, path1)
        val accessor2 = VariableAccessor(variable1, path1)
        val accessor3 = VariableAccessor(variable2, path1)
        val accessor4 = VariableAccessor(variable1, path2)
        val accessor5 = VariableAccessor(variable1, null)
        val accessor6 = VariableAccessor(variable1, null)

        // Test equals
        assertEquals(accessor1, accessor2)
        assertNotEquals(accessor1, accessor3)
        assertNotEquals(accessor1, accessor4)
        assertNotEquals(accessor1, accessor5)
        assertEquals(accessor5, accessor6)

        // Test hashCode
        assertEquals(accessor1.hashCode(), accessor2.hashCode())
        assertNotEquals(accessor1.hashCode(), accessor3.hashCode())
        assertNotEquals(accessor1.hashCode(), accessor4.hashCode())
        assertNotEquals(accessor1.hashCode(), accessor5.hashCode())
        assertEquals(accessor5.hashCode(), accessor6.hashCode())
    }
}
