package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.ReferenceContainer.Converter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReferenceContainerTests {

    @Test
    fun key_Returns_ReferenceContainer_With_Single_Level_Path() {
        val reference = ReferenceContainer.key("container")
        val expected = JsonPath["container"]
        assertEquals(expected, reference.path)
    }

    @Test
    fun path_Returns_ReferenceContainer_With_Delegated_Path() {
        val reference = ReferenceContainer.path(JsonPath["container"]["sub-path"][0])
        val expected = JsonPath["container"]["sub-path"][0]
        assertEquals(expected, reference.path)
    }

    @Test
    fun asDataObject_Serializes_Key_References_Under_Key() {
        val reference = ReferenceContainer.key("container")
        val expected = DataObject.create {
            put(Converter.KEY_KEY, "container")
        }
        assertEquals(expected, reference.asDataObject())
    }

    @Test
    fun asDataObject_Serializes_Single_Level_Path_References_Under_Path() {
        val reference = ReferenceContainer.path(JsonPath["container"])
        val expected = DataObject.create {
            put(Converter.KEY_PATH, "container")
        }
        assertEquals(expected, reference.asDataObject())
    }

    @Test
    fun asDataObject_Serializes_Multi_Level_Path_References_Under_Path() {
        val reference = ReferenceContainer.path(JsonPath["container"][0][""])
        val expected = DataObject.create {
            put(Converter.KEY_PATH, "container[0][\"\"]")
        }
        assertEquals(expected, reference.asDataObject())
    }

    @Test
    fun converter_Returns_Key_Reference_When_Key_Is_Present() {
        val dataObject = DataObject.create {
            put(Converter.KEY_KEY, "container")
        }
        val expected = ReferenceContainer.key("container")
        assertEquals(expected, Converter.convert(dataObject.asDataItem()))
    }

    @Test
    fun converter_Returns_Path_Reference_When_Path_Is_Present() {
        val dataObject = DataObject.create {
            put(Converter.KEY_PATH, "container")
        }
        val expected = ReferenceContainer.path(JsonPath["container"])
        assertEquals(expected, Converter.convert(dataObject.asDataItem()))
    }

    @Test
    fun converter_Returns_Path_Reference_When_Both_Key_And_Path_Are_Present() {
        val dataObject = DataObject.create {
            put(Converter.KEY_PATH, "path")
            put(Converter.KEY_KEY, "key")
        }
        val expected = ReferenceContainer.path(JsonPath["path"])
        assertEquals(expected, Converter.convert(dataObject.asDataItem()))
    }

    @Test
    fun converter_Returns_Null_Reference_When_Neither_Key_Nor_Path_Is_Present() {
        val dataObject = DataObject.EMPTY_OBJECT
        assertNull(Converter.convert(dataObject.asDataItem()))
    }

    @Test
    fun converter_Returns_Null_Reference_When_Path_Is_Array_Path() {
        val dataObject = DataObject.create {
            put(Converter.KEY_PATH, "[0].property")
        }
        assertNull(Converter.convert(dataObject.asDataItem()))
    }
}