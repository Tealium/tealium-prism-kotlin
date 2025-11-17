package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.DataList.Companion.EMPTY_LIST
import com.tealium.prism.core.api.data.DataObject.Companion.EMPTY_OBJECT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonPathExtractableTests {

    val simpleObject = DataObject.create {
        put("string", "value")
        put("int", 1)
        put("double", 1.1)
        put("long", 100L)
        put("boolean", false)
        put("list", EMPTY_LIST)
        put("obj", EMPTY_OBJECT)
    }

    val simpleList = DataList.create {
        add("value")
        add(1)
        add(1.1)
        add(100L)
        add(false)
        add(EMPTY_LIST)
        add(EMPTY_OBJECT)
    }

    val complexList = DataList.create {
        add(simpleObject)
        add(simpleList)
    }

    val complexObject = DataObject.create {
        put("obj", simpleObject)
        put("list", complexList)
    }

    @Test
    fun extract_Returns_Null_When_Object_Missing_From_DataObject() {
        assertNull(simpleObject.extractString(JsonPath["missing"]))
    }

    @Test
    fun extract_Returns_Null_When_Object_Missing_From_DataList() {
        assertNull(simpleList.extractString(JsonPath[10]))
    }

    @Test
    fun extract_Returns_Null_When_Nested_Type_Is_Not_Correct_Type() {
        assertNull(complexObject.extractString(JsonPath["list"]["1"]))
        assertNull(complexList.extractString(JsonPath[0][1]))
    }

    @Test
    fun extract_Extracts_DataItem_From_First_Level_Of_DataObject() {
        assertEquals("value", simpleObject.extractString(JsonPath["string"]))
        assertEquals(1, simpleObject.extractInt(JsonPath["int"]))
        assertEquals(1.1, simpleObject.extractDouble(JsonPath["double"]))
        assertEquals(100L, simpleObject.extractLong(JsonPath["long"]))
        assertEquals(false, simpleObject.extractBoolean(JsonPath["boolean"]))
        assertEquals(EMPTY_LIST, simpleObject.extractDataList(JsonPath["list"]))
        assertEquals(EMPTY_OBJECT, simpleObject.extractDataObject(JsonPath["obj"]))
    }

    @Test
    fun extract_Extracts_DataItem_From_First_Level_Of_DataList() {
        assertEquals("value", simpleList.extractString(JsonPath[0]))
        assertEquals(1, simpleList.extractInt(JsonPath[1]))
        assertEquals(1.1, simpleList.extractDouble(JsonPath[2]))
        assertEquals(100L, simpleList.extractLong(JsonPath[3]))
        assertEquals(false, simpleList.extractBoolean(JsonPath[4]))
        assertEquals(EMPTY_LIST, simpleList.extractDataList(JsonPath[5]))
        assertEquals(EMPTY_OBJECT, simpleList.extractDataObject(JsonPath[6]))
    }

    @Test
    fun extract_Extracts_DataItem_From_DataObject_Inside_Of_DataObject() {
        assertEquals("value", complexObject.extractString(JsonPath["obj"]["string"]))
        assertEquals(1, complexObject.extractInt(JsonPath["obj"]["int"]))
        assertEquals(1.1, complexObject.extractDouble(JsonPath["obj"]["double"]))
        assertEquals(100L, complexObject.extractLong(JsonPath["obj"]["long"]))
        assertEquals(false, complexObject.extractBoolean(JsonPath["obj"]["boolean"]))
        assertEquals(EMPTY_LIST, complexObject.extractDataList(JsonPath["obj"]["list"]))
        assertEquals(EMPTY_OBJECT, complexObject.extractDataObject(JsonPath["obj"]["obj"]))
    }

    @Test
    fun extract_Extracts_DataItem_From_DataObject_Inside_Of_DataList() {
        assertEquals("value", complexList.extractString(JsonPath[0]["string"]))
        assertEquals(1, complexList.extractInt(JsonPath[0]["int"]))
        assertEquals(1.1, complexList.extractDouble(JsonPath[0]["double"]))
        assertEquals(100L, complexList.extractLong(JsonPath[0]["long"]))
        assertEquals(false, complexList.extractBoolean(JsonPath[0]["boolean"]))
        assertEquals(EMPTY_LIST, complexList.extractDataList(JsonPath[0]["list"]))
        assertEquals(EMPTY_OBJECT, complexList.extractDataObject(JsonPath[0]["obj"]))
    }

    @Test
    fun extract_Extracts_DataItem_From_DataList_Inside_Of_DataList() {
        assertEquals("value", complexList.extractString(JsonPath[1][0]))
        assertEquals(1, complexList.extractInt(JsonPath[1][1]))
        assertEquals(1.1, complexList.extractDouble(JsonPath[1][2]))
        assertEquals(100L, complexList.extractLong(JsonPath[1][3]))
        assertEquals(false, complexList.extractBoolean(JsonPath[1][4]))
        assertEquals(EMPTY_LIST, complexList.extractDataList(JsonPath[1][5]))
        assertEquals(EMPTY_OBJECT, complexList.extractDataObject(JsonPath[1][6]))
    }

    @Test
    fun extract_Extracts_DataItem_From_DataObject_Inside_Of_DataList_Inside_Of_DataObject() {
        assertEquals("value", complexObject.extractString(JsonPath["list"][0]["string"]))
        assertEquals(1, complexObject.extractInt(JsonPath["list"][0]["int"]))
        assertEquals(1.1, complexObject.extractDouble(JsonPath["list"][0]["double"]))
        assertEquals(100L, complexObject.extractLong(JsonPath["list"][0]["long"]))
        assertEquals(false, complexObject.extractBoolean(JsonPath["list"][0]["boolean"]))
        assertEquals(EMPTY_LIST, complexObject.extractDataList(JsonPath["list"][0]["list"]))
        assertEquals(EMPTY_OBJECT, complexObject.extractDataObject(JsonPath["list"][0]["obj"]))
    }

    @Test
    fun extract_Extracts_DataItem_From_DataList_Inside_Of_DataList_Inside_Of_DataObject() {
        assertEquals("value", complexObject.extractString(JsonPath["list"][1][0]))
        assertEquals(1, complexObject.extractInt(JsonPath["list"][1][1]))
        assertEquals(1.1, complexObject.extractDouble(JsonPath["list"][1][2]))
        assertEquals(100L, complexObject.extractLong(JsonPath["list"][1][3]))
        assertEquals(false, complexObject.extractBoolean(JsonPath["list"][1][4]))
        assertEquals(EMPTY_LIST, complexObject.extractDataList(JsonPath["list"][1][5]))
        assertEquals(EMPTY_OBJECT, complexObject.extractDataObject(JsonPath["list"][1][6]))
    }

    @Test
    fun extract_Extracts_DataItem_From_DataObject_Using_Converter() {
        val convertedString: String? = complexObject.extract(
            JsonPath["list"][0]["string"],
            converter = DataItem::getString
        )
        assertEquals("value", convertedString)
    }

    @Test
    fun extract_Extracts_DataItem_From_DataList_Using_Converter() {
        val convertedString: String? = complexList.extract(
            JsonPath[1][0],
            converter = DataItem::getString
        )
        assertEquals("value", convertedString)
    }

    @Test
    fun extract_Extracts_DataItem_Using_Multilingual_Keys() {
        val obj = DataObject.create { put("mültilíngual", "válüe") }
        assertEquals("válüe", obj.extractString(JsonPath["mültilíngual"]))
    }

    @Test
    fun extract_Extracts_Nested_DataItem_Using_Multilingual_Keys() {
        val obj = DataObject.create {
            put("mülti", DataObject.create {
                put("línguál", "válüe")
            })
        }
        assertEquals("válüe", obj.extractString(JsonPath["mülti"]["línguál"]))
    }
}