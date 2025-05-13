package com.tealium.core.internal.settings

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.settings.ValueContainer
import com.tealium.core.api.settings.VariableAccessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MappingParametersTests {

    @Test
    fun constructor_Sets_Properties_Correctly() {
        val key = VariableAccessor("test_variable", listOf("path1"))
        val filter = ValueContainer("test_filter")
        val mapTo = ValueContainer("test_map_to")

        val parameters = MappingParameters(key, filter, mapTo)

        assertEquals(key, parameters.key)
        assertEquals(filter, parameters.filter)
        assertEquals(mapTo, parameters.mapTo)
    }

    @Test
    fun constructor_Accepts_Null_Filter_And_MapTo() {
        val key = VariableAccessor("test_variable", null)

        val parameters = MappingParameters(key, null, null)

        assertEquals(key, parameters.key)
        assertNull(parameters.filter)
        assertNull(parameters.mapTo)
    }

    @Test
    fun asDataObject_Returns_Correct_DataObject_With_All_Fields() {
        val key = VariableAccessor("test_variable", listOf("path1"))
        val filter = ValueContainer("test_filter")
        val mapTo = ValueContainer("test_map_to")
        val parameters = MappingParameters(key, filter, mapTo)

        val dataObject = parameters.asDataObject()

        assertNotNull(dataObject)
        val keyAccessor = dataObject.get(MappingParameters.Converter.KEY_KEY, VariableAccessor.Converter)
        assertEquals(key, keyAccessor)
        assertEquals(filter, dataObject.get(MappingParameters.Converter.KEY_FILTER, ValueContainer.Converter))
        assertEquals(mapTo, dataObject.get(MappingParameters.Converter.KEY_MAP_TO, ValueContainer.Converter))
    }

    @Test
    fun asDataObject_Returns_Correct_DataObject_With_Null_Fields() {
        val key = VariableAccessor("test_variable", null)
        val parameters = MappingParameters(key, null, null)

        val dataObject = parameters.asDataObject()

        assertNotNull(dataObject)
        val keyAccessor = dataObject.get(MappingParameters.Converter.KEY_KEY, VariableAccessor.Converter)
        assertEquals(key, keyAccessor)
        assertNull(dataObject.get(MappingParameters.Converter.KEY_FILTER, ValueContainer.Converter))
        assertNull(dataObject.get(MappingParameters.Converter.KEY_MAP_TO, ValueContainer.Converter))
    }

    @Test
    fun converter_Converts_DataObject_To_MappingParameters_Correctly_With_All_Fields() {
        val variable = "test_variable"
        val path = listOf("path1")
        val filter = "test_filter"
        val mapTo = "test_map_to"

        val dataObject = DataObject.create {
            put(MappingParameters.Converter.KEY_KEY, VariableAccessor(variable, path))
            put(MappingParameters.Converter.KEY_FILTER, ValueContainer(filter))
            put(MappingParameters.Converter.KEY_MAP_TO, ValueContainer(mapTo))
        }

        val parameters = MappingParameters.Converter.convert(dataObject.asDataItem())!!

        assertEquals(variable, parameters.key?.variable)
        assertEquals(path, parameters.key?.path)
        assertEquals(filter, parameters.filter?.value)
        assertEquals(mapTo, parameters.mapTo?.value)
    }

    @Test
    fun converter_Converts_DataObject_To_MappingParameters_Correctly_With_Null_Fields() {
        val variable = "test_variable"

        val dataObject = DataObject.create {
            put(MappingParameters.Converter.KEY_KEY, VariableAccessor(variable, null))
        }

        val parameters = MappingParameters.Converter.convert(dataObject.asDataItem())!!

        assertEquals(variable, parameters.key?.variable)
        assertNull(parameters.key?.path)
        assertNull(parameters.filter)
        assertNull(parameters.mapTo)
    }

    @Test
    fun converter_Returns_Null_When_DataObject_Is_Null() {
        val parameters = MappingParameters.Converter.convert(DataItem.NULL)

        assertNull(parameters)
    }

    @Test
    fun converter_Returns_Null_When_Both_Key_And_MapTo_Are_Missing() {
        val dataObject = DataObject.create {
            put(MappingParameters.Converter.KEY_FILTER, ValueContainer("filter"))
        }

        val parameters = MappingParameters.Converter.convert(dataObject.asDataItem())

        assertNull(parameters)
    }

    @Test
    fun equals_And_HashCode_Are_Correct() {
        val key1 = VariableAccessor("variable1", listOf("path1"))
        val key2 = VariableAccessor("variable2", listOf("path2"))
        val filter1 = ValueContainer("filter1")
        val filter2 = ValueContainer("filter2")
        val mapTo1 = ValueContainer("mapTo1")
        val mapTo2 = ValueContainer("mapTo2")

        val params1 = MappingParameters(key1, filter1, mapTo1)
        val params2 = MappingParameters(key1, filter1, mapTo1)
        val params3 = MappingParameters(key2, filter1, mapTo1)
        val params4 = MappingParameters(key1, filter2, mapTo1)
        val params5 = MappingParameters(key1, filter1, mapTo2)
        val params6 = MappingParameters(key1, null, mapTo1)
        val params7 = MappingParameters(key1, filter1, null)
        val params8 = MappingParameters(key1, null, null)
        val params9 = MappingParameters(key1, null, null)

        // Test equals
        assertEquals(params1, params2)
        assertNotEquals(params1, params3)
        assertNotEquals(params1, params4)
        assertNotEquals(params1, params5)
        assertNotEquals(params1, params6)
        assertNotEquals(params1, params7)
        assertNotEquals(params6, params7)
        assertEquals(params8, params9)

        // Test hashCode
        assertEquals(params1.hashCode(), params2.hashCode())
        assertNotEquals(params1.hashCode(), params3.hashCode())
        assertNotEquals(params1.hashCode(), params4.hashCode())
        assertNotEquals(params1.hashCode(), params5.hashCode())
        assertNotEquals(params1.hashCode(), params6.hashCode())
        assertNotEquals(params1.hashCode(), params7.hashCode())
        assertEquals(params8.hashCode(), params9.hashCode())
    }
}
