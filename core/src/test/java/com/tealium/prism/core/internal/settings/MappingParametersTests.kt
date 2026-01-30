package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.data.get
import com.tealium.prism.core.api.settings.MappingParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MappingParametersTests {

    @Test
    fun constructor_Sets_Properties_Correctly() {
        val key = ReferenceContainer.path(JsonPath["path1"]["test_variable"])
        val filter = ValueContainer("test_filter")
        val mapTo = ValueContainer("test_map_to")

        val parameters = MappingParameters(key, filter, mapTo)

        assertEquals(key, parameters.reference)
        assertEquals(filter, parameters.filter)
        assertEquals(mapTo, parameters.mapTo)
    }

    @Test
    fun constructor_Accepts_Null_Filter_And_MapTo() {
        val key = ReferenceContainer.key("test_variable")

        val parameters = MappingParameters(key, null, null)

        assertEquals(key, parameters.reference)
        assertNull(parameters.filter)
        assertNull(parameters.mapTo)
    }

    @Test
    fun asDataObject_Returns_Correct_DataObject_With_All_Fields() {
        val key = ReferenceContainer.path(JsonPath["path1"]["test_variable"])
        val filter = ValueContainer("test_filter")
        val mapTo = ValueContainer("test_map_to")
        val parameters = MappingParameters(key, filter, mapTo)

        val dataObject = parameters.asDataObject()

        assertNotNull(dataObject)
        val keyAccessor =
            dataObject.get(MappingParameters.Converter.KEY_REFERENCE, ReferenceContainer.Converter)
        assertEquals(key, keyAccessor)
        assertEquals(
            filter,
            dataObject.get(MappingParameters.Converter.KEY_FILTER, ValueContainer.Converter)
        )
        assertEquals(
            mapTo,
            dataObject.get(MappingParameters.Converter.KEY_MAP_TO, ValueContainer.Converter)
        )
    }

    @Test
    fun asDataObject_Returns_Correct_DataObject_With_Null_Fields() {
        val key = ReferenceContainer.key("test_variable")
        val parameters = MappingParameters(key, null, null)

        val dataObject = parameters.asDataObject()

        assertNotNull(dataObject)
        val keyAccessor =
            dataObject.get(MappingParameters.Converter.KEY_REFERENCE, ReferenceContainer.Converter)
        assertEquals(key, keyAccessor)
        assertNull(dataObject.get(MappingParameters.Converter.KEY_FILTER, ValueContainer.Converter))
        assertNull(dataObject.get(MappingParameters.Converter.KEY_MAP_TO, ValueContainer.Converter))
    }

    @Test
    fun converter_Converts_DataObject_To_MappingParameters_Correctly_With_All_Fields() {
        val path = JsonPath["path1"]["test_variable"]
        val reference = ReferenceContainer.path(path)
        val filter = "test_filter"
        val mapTo = "test_map_to"

        val dataObject = DataObject.create {
            put(MappingParameters.Converter.KEY_REFERENCE, reference)
            put(MappingParameters.Converter.KEY_FILTER, ValueContainer(filter))
            put(MappingParameters.Converter.KEY_MAP_TO, ValueContainer(mapTo))
        }

        val parameters = MappingParameters.Converter.convert(dataObject.asDataItem())!!

        assertEquals(path, parameters.reference?.path)
        assertEquals(filter, parameters.filter?.value)
        assertEquals(mapTo, parameters.mapTo?.value)
    }

    @Test
    fun converter_Converts_DataObject_To_MappingParameters_Correctly_With_Null_Fields() {
        val dataObject = DataObject.create {
            put(MappingParameters.Converter.KEY_REFERENCE, ReferenceContainer.key("test_variable"))
        }

        val parameters = MappingParameters.Converter.convert(dataObject.asDataItem())!!

        assertEquals(JsonPath["test_variable"], parameters.reference?.path)
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
        val key1 = ReferenceContainer.path(JsonPath["path1"]["variable1"])
        val key2 = ReferenceContainer.path(JsonPath["path2"]["variable2"])
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
