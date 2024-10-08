package com.tealium.core.api.data

import com.tealium.core.api.data.DataItemUtils.asDataItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@Suppress("KotlinConstantConditions")
@RunWith(RobolectricTestRunner::class)
class DataItemTests {

    private val string = "string"
    private val int = 10
    private val double = 111.111
    private val long = 100L
    private val booleanFalse = false
    private val booleanTrue = true
    private val list = DataList.create {
        add(1)
    }
    private val dataObject = DataObject.create {
        put("string", "value")
    }

    private val stringItem: DataItem = DataItem.string(string)
    private val intItem: DataItem = DataItem.int(int)
    private val doubleItem: DataItem = DataItem.double(double)
    private val longItem: DataItem = DataItem.long(long)
    private val falseItem: DataItem = DataItem.boolean(booleanFalse)
    private val trueItem: DataItem = DataItem.boolean(booleanTrue)
    private val listItem: DataItem = list.asDataItem()
    private val dataObjectItem: DataItem = dataObject.asDataItem()

    @Test
    fun isDataObject_ReturnsTrue_WhenValueIsDataObject() {
        assertTrue(dataObjectItem.isDataObject())
    }

    @Test
    fun isDataObject_ReturnsFalse_WhenValueIsNotDataObject() {
        assertFalse(stringItem.isDataObject())
        assertFalse(intItem.isDataObject())
        assertFalse(doubleItem.isDataObject())
        assertFalse(longItem.isDataObject())
        assertFalse(falseItem.isDataObject())
        assertFalse(trueItem.isDataObject())
        assertFalse(listItem.isDataObject())
    }

    @Test
    fun isList_ReturnsTrue_WhenValueIsList() {
        assertTrue(listItem.isDataList())
    }

    @Test
    fun isList_ReturnsFalse_WhenValueIsNotList() {
        assertFalse(stringItem.isDataList())
        assertFalse(intItem.isDataList())
        assertFalse(doubleItem.isDataList())
        assertFalse(longItem.isDataList())
        assertFalse(falseItem.isDataList())
        assertFalse(trueItem.isDataList())
        assertFalse(dataObjectItem.isDataList())
    }

    @Test
    fun isString_ReturnsTrue_WhenValueIsString() {
        assertTrue(stringItem.isString())
    }

    @Test
    fun isString_ReturnsFalse_WhenValueIsNotString() {
        assertFalse(intItem.isString())
        assertFalse(doubleItem.isString())
        assertFalse(longItem.isString())
        assertFalse(falseItem.isString())
        assertFalse(trueItem.isString())
        assertFalse(listItem.isString())
        assertFalse(dataObjectItem.isString())
    }

    @Test
    fun isInt_ReturnsTrue_WhenValueIsInt() {
        assertTrue(intItem.isInt())
    }

    @Test
    fun isInt_ReturnsFalse_WhenValueIsNotInt() {
        assertFalse(stringItem.isInt())
        assertFalse(doubleItem.isInt())
        assertFalse(longItem.isInt())
        assertFalse(falseItem.isInt())
        assertFalse(trueItem.isInt())
        assertFalse(listItem.isInt())
        assertFalse(dataObjectItem.isInt())
    }

    @Test
    fun isDouble_ReturnsTrue_WhenValueIsDouble() {
        assertTrue(doubleItem.isDouble())
    }

    @Test
    fun isDouble_ReturnsFalse_WhenValueIsNotDouble() {
        assertFalse(stringItem.isDouble())
        assertFalse(intItem.isDouble())
        assertFalse(longItem.isDouble())
        assertFalse(falseItem.isDouble())
        assertFalse(trueItem.isDouble())
        assertFalse(listItem.isDouble())
        assertFalse(dataObjectItem.isDouble())
    }

    @Test
    fun isLong_ReturnsTrue_WhenValueIsLong() {
        assertTrue(longItem.isLong())
    }

    @Test
    fun isLong_ReturnsFalse_WhenValueIsNotLong() {
        assertFalse(stringItem.isLong())
        assertFalse(intItem.isLong())
        assertFalse(doubleItem.isLong())
        assertFalse(falseItem.isLong())
        assertFalse(trueItem.isLong())
        assertFalse(listItem.isLong())
        assertFalse(dataObjectItem.isLong())
    }

    @Test
    fun isBoolean_ReturnsTrue_WhenValueIsBoolean() {
        assertTrue(falseItem.isBoolean())
        assertTrue(trueItem.isBoolean())
    }

    @Test
    fun isBoolean_ReturnsFalse_WhenValueIsNotBoolean() {
        assertFalse(stringItem.isBoolean())
        assertFalse(intItem.isBoolean())
        assertFalse(doubleItem.isBoolean())
        assertFalse(longItem.isBoolean())
        assertFalse(listItem.isBoolean())
        assertFalse(dataObjectItem.isBoolean())
    }

    @Test
    fun isNumber_ReturnsTrue_WhenValueIsNumber() {
        assertTrue(intItem.isNumber())
        assertTrue(doubleItem.isNumber())
        assertTrue(longItem.isNumber())
    }

    @Test
    fun isNumber_ReturnsFalse_WhenValueIsNotNumber() {
        assertFalse(stringItem.isNumber())
        assertFalse(falseItem.isNumber())
        assertFalse(trueItem.isNumber())
        assertFalse(listItem.isNumber())
        assertFalse(dataObjectItem.isNumber())
    }


    @Test
    fun getDataObject_ReturnsDataObject_WhenValueIsDataObject() {
        assertNotNull(dataObjectItem.getDataObject())
        assertEquals(dataObject, dataObjectItem.getDataObject())
    }

    @Test
    fun getDataObject_ReturnsNull_WhenValueIsNotDataObject() {
        assertNull(stringItem.getDataObject())
        assertNull(intItem.getDataObject())
        assertNull(doubleItem.getDataObject())
        assertNull(longItem.getDataObject())
        assertNull(falseItem.getDataObject())
        assertNull(trueItem.getDataObject())
        assertNull(listItem.getDataObject())
    }

    @Test
    fun getList_ReturnsList_WhenValueIsList() {
        assertNotNull(listItem.getDataList())
        assertEquals(list, listItem.getDataList())
    }

    @Test
    fun getList_ReturnsNull_WhenValueIsNotList() {
        assertNull(stringItem.getDataList())
        assertNull(intItem.getDataList())
        assertNull(doubleItem.getDataList())
        assertNull(longItem.getDataList())
        assertNull(falseItem.getDataList())
        assertNull(trueItem.getDataList())
        assertNull(dataObjectItem.getDataList())
    }

    @Test
    fun getString_ReturnsString_WhenValueIsString() {
        assertNotNull(stringItem.getString())
        assertEquals(string, stringItem.getString())
    }

    @Test
    fun getString_ReturnsNull_WhenValueIsNotString() {
        assertNull(intItem.getString())
        assertNull(doubleItem.getString())
        assertNull(longItem.getString())
        assertNull(falseItem.getString())
        assertNull(trueItem.getString())
        assertNull(listItem.getString())
        assertNull(dataObjectItem.getString())
    }

    @Test
    fun getInt_ReturnsInt_WhenValueIsNumber() {
        assertNotNull(intItem.getInt())
        assertNotNull(doubleItem.getInt())
        assertNotNull(longItem.getInt())

        assertEquals(int, intItem.getInt())
        assertEquals(double.toInt(), doubleItem.getInt())
        assertEquals(long.toInt(), longItem.getInt())
    }

    @Test
    fun getInt_ReturnsNull_WhenValueIsNotNumber() {
        assertNull(stringItem.getInt())
        assertNull(falseItem.getInt())
        assertNull(trueItem.getInt())
        assertNull(listItem.getInt())
        assertNull(dataObjectItem.getInt())
    }

    @Test
    fun getDouble_ReturnsDouble_WhenValueIsNumber() {
        assertNotNull(intItem.getDouble())
        assertNotNull(doubleItem.getDouble())
        assertNotNull(longItem.getDouble())

        assertEquals(int.toDouble(), intItem.getDouble())
        assertEquals(double, doubleItem.getDouble())
        assertEquals(long.toDouble(), longItem.getDouble())
    }

    @Test
    fun getDouble_ReturnsNull_WhenValueIsNotDouble() {
        assertNull(stringItem.getDouble())
        assertNull(falseItem.getDouble())
        assertNull(trueItem.getDouble())
        assertNull(listItem.getDouble())
        assertNull(dataObjectItem.getDouble())
    }

    @Test
    fun getLong_ReturnsLong_WhenValueIsLong() {
        assertNotNull(intItem.getLong())
        assertNotNull(doubleItem.getLong())
        assertNotNull(longItem.getLong())

        assertEquals(int.toLong(), intItem.getLong())
        assertEquals(double.toLong(), doubleItem.getLong())
        assertEquals(long, longItem.getLong())
    }

    @Test
    fun getLong_ReturnsNull_WhenValueIsNotLong() {
        assertNull(stringItem.getLong())
        assertNull(falseItem.getLong())
        assertNull(trueItem.getLong())
        assertNull(listItem.getLong())
        assertNull(dataObjectItem.getLong())
    }

    @Test
    fun getBoolean_ReturnsBoolean_WhenValueIsBoolean() {
        assertFalse(falseItem.getBoolean()!!)
        assertTrue(trueItem.getBoolean()!!)
    }

    @Test
    fun getBoolean_ReturnsNull_WhenValueIsNotBoolean() {
        assertNull(stringItem.getBoolean())
        assertNull(intItem.getBoolean())
        assertNull(doubleItem.getBoolean())
        assertNull(longItem.getBoolean())
        assertNull(listItem.getBoolean())
        assertNull(dataObjectItem.getBoolean())
    }


    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualDataObject() {
        assertTrue(dataObjectItem == dataObjectItem)

        val copy = dataObject.copy().asDataItem()
        assertTrue(dataObjectItem == copy)
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotDataObject() {
        assertFalse(dataObjectItem == stringItem)
        assertFalse(dataObjectItem == intItem)
        assertFalse(dataObjectItem == doubleItem)
        assertFalse(dataObjectItem == longItem)
        assertFalse(dataObjectItem == falseItem)
        assertFalse(dataObjectItem == trueItem)
        assertFalse(dataObjectItem == listItem)
    }

    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualList() {
        assertTrue(listItem == listItem)

        val copy = list.copy().asDataItem()
        assertTrue(listItem == copy)
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotList() {
        assertFalse(listItem == stringItem)
        assertFalse(listItem == intItem)
        assertFalse(listItem == doubleItem)
        assertFalse(listItem == longItem)
        assertFalse(listItem == falseItem)
        assertFalse(listItem == trueItem)
        assertFalse(listItem == dataObjectItem)
    }

    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualString() {
        assertTrue(stringItem == stringItem)

        assertTrue(stringItem == DataItem.string(string))
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotString() {
        assertFalse(stringItem == intItem)
        assertFalse(stringItem == doubleItem)
        assertFalse(stringItem == longItem)
        assertFalse(stringItem == falseItem)
        assertFalse(stringItem == trueItem)
        assertFalse(stringItem == listItem)
        assertFalse(stringItem == dataObjectItem)
    }

    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualBoolean() {
        assertTrue(trueItem == trueItem)
        assertTrue(falseItem == falseItem)

        assertTrue(trueItem == DataItem.boolean(true))
        assertTrue(falseItem == DataItem.boolean(false))
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotEqualBoolean() {
        assertFalse(trueItem == stringItem)
        assertFalse(trueItem == intItem)
        assertFalse(trueItem == doubleItem)
        assertFalse(trueItem == longItem)
        assertFalse(trueItem == falseItem)
        assertFalse(trueItem == listItem)
        assertFalse(trueItem == dataObjectItem)

        assertFalse(falseItem == stringItem)
        assertFalse(falseItem == intItem)
        assertFalse(falseItem == doubleItem)
        assertFalse(falseItem == longItem)
        assertFalse(falseItem == trueItem)
        assertFalse(falseItem == listItem)
        assertFalse(falseItem == dataObjectItem)
    }

    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualDouble() {
        assertTrue(doubleItem == doubleItem)

        assertTrue(doubleItem == DataItem.double(double))
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotEqualDouble() {
        assertFalse(doubleItem == stringItem)
        assertFalse(doubleItem == intItem)
        assertFalse(doubleItem == longItem)
        assertFalse(doubleItem == falseItem)
        assertFalse(doubleItem == trueItem)
        assertFalse(doubleItem == listItem)
        assertFalse(doubleItem == dataObjectItem)
    }

    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualInt() {
        assertTrue(intItem == intItem)

        assertTrue(intItem == DataItem.int(int))
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotEqualInt() {
        assertFalse(intItem == stringItem)
        assertFalse(intItem == longItem)
        assertFalse(intItem == doubleItem)
        assertFalse(intItem == falseItem)
        assertFalse(intItem == trueItem)
        assertFalse(intItem == listItem)
        assertFalse(intItem == dataObjectItem)
    }

    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualLong() {
        assertTrue(longItem == longItem)

        assertTrue(longItem == DataItem.long(long))
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotEqualLong() {
        assertFalse(longItem == stringItem)
        assertFalse(longItem == intItem)
        assertFalse(longItem == doubleItem)
        assertFalse(longItem == falseItem)
        assertFalse(longItem == trueItem)
        assertFalse(longItem == listItem)
        assertFalse(longItem == dataObjectItem)
    }

    @Test
    fun equals_ReturnsTrue_WhenNumericValuesAreEqual() {
        assertTrue(DataItem.long(1L) == DataItem.int(1))
        assertTrue(DataItem.int(1) == DataItem.long(1L))

        assertTrue(DataItem.double(10.0) == DataItem.int(10))
        assertTrue(DataItem.double(10.0) == DataItem.long(10))
        assertTrue(DataItem.int(10) == DataItem.double(10.0))
        assertTrue(DataItem.long(10) == DataItem.double(10.0))
    }

    @Test
    fun convert_Null_Returns_DataItemNull() {
        val value = DataItem.convert(null)

        assertEquals(DataItem.NULL, value)
    }

    @Test
    fun convert_JSONObject_Null_Returns_DataItemNull() {
        val value = DataItem.convert(JSONObject.NULL)

        assertEquals(DataItem.NULL, value)
    }

    @Test
    fun convert_DataItemNull_Returns_DataItemNull() {
        val value = DataItem.convert(DataItem.NULL)

        assertEquals(DataItem.NULL, value)
    }

    @Test
    fun convert_String_Returns_DataItemString() {
        val values = listOf(DataItem.convert("test"), "test".asDataItem())

        for (value in values) {
            assertTrue(value.isString())
            assertEquals("test", value.value)
        }
    }

    @Test
    fun convert_Int_Returns_DataItemInt() {
        val values = listOf(DataItem.convert(10), 10.asDataItem())

        for (value in values) {
            assertTrue(value.isInt())
            assertEquals(10, value.value)
        }
    }

    @Test
    fun convert_Long_Returns_DataItemLong() {
        val values = listOf(DataItem.convert(100L), 100L.asDataItem())

        for (value in values) {
            assertTrue(value.isLong())
            assertEquals(100L, value.value)
        }
    }

    @Test
    fun convert_Double_Returns_DataItemDouble() {
        val values = listOf(DataItem.convert(100.111), 100.111.asDataItem())

        for (value in values) {
            assertTrue(value.isDouble())
            assertEquals(100.111, value.value)
        }
    }

    @Test
    fun convert_Double_Returns_Null_When_Using_Non_Thrown_Conversion() {
        val values = listOf(
            DataItem.double(Double.NaN),
            DataItem.double(Double.POSITIVE_INFINITY),
            DataItem.double(Double.NEGATIVE_INFINITY),
            Double.NaN.asDataItem(),
            Double.POSITIVE_INFINITY.asDataItem(),
            Double.NEGATIVE_INFINITY.asDataItem()
        )

        for (value in values) {
            assertTrue(value.isNull())
            assertSame(DataItem.NULL, value)
        }
    }

    @Test(expected = UnsupportedDataItemException::class)
    fun convert_Double_NaN_Throws() {
        DataItem.convert(Double.NaN)
    }

    @Test(expected = UnsupportedDataItemException::class)
    fun convert_Double_NegativeInfinity_Throws() {
        DataItem.convert(Double.POSITIVE_INFINITY)
    }

    @Test(expected = UnsupportedDataItemException::class)
    fun convert_Double_PositiveInfinity_Throws() {
        DataItem.convert(Double.NEGATIVE_INFINITY)
    }

    @Test
    fun convert_Boolean_Returns_DataItemBoolean() {
        val values = listOf(DataItem.convert(false), false.asDataItem())

        for (value in values) {
            assertTrue(value.isBoolean())
            assertEquals(false, value.value)
        }
    }

    @Test
    fun convert_DataList_Returns_DataItemList() {
        val value = DataItem.convert(DataList.create {
            add(1)
            add(2)
            add(3)
        })

        assertTrue(value.isDataList())
        assertEquals(DataList.create {
            add(1)
            add(2)
            add(3)
        }, value.getDataList())
    }

    @Test
    fun convert_DataObject_Returns_DataItemDataObject() {
        val value = DataItem.convert(DataObject.create {
            put("key", "value")
        })

        assertTrue(value.isDataObject())
        assertEquals("value", value.getDataObject()!!.getString("key"))
    }

    @Test
    fun convert_Collection_Returns_DataItemList() {
        val value = DataItem.convert(listOf(1, 2, 3))

        assertTrue(value.isDataList())
        assertEquals(DataList.create {
            add(1)
            add(2)
            add(3)
        }, value.value)
    }

    @Test
    fun convert_Array_Returns_DataItemList() {
        val value = DataItem.convert(arrayOf(1, 2, 3))

        assertTrue(value.isDataList())
        assertEquals(DataList.create {
            add(1)
            add(2)
            add(3)
        }, value.value)
    }

    @Test
    fun convert_Map_Returns_DataItemDataObject() {
        val value = DataItem.convert(
            mapOf(
                "string" to "value",
                "int" to 10,
                "long" to 100L,
                "double" to 111.111,
                "boolean" to true
            )
        )

        val valueDataObject = value.getDataObject()!!

        assertTrue(value.isDataObject())
        assertEquals("value", valueDataObject.getString("string"))
        assertEquals(10, valueDataObject.getInt("int"))
        assertEquals(100L, valueDataObject.getLong("long"))
        assertEquals(111.111, valueDataObject.getDouble("double"))
        assertEquals(true, valueDataObject.getBoolean("boolean"))
    }

    @Test
    fun convert_JSONArray_Returns_DataItemList() {
        val value = DataItem.convert(JSONArray().apply {
            put("value")
            put(10)
            put(100L)
            put(111.111)
            put(true)
            put(JSONArray().apply {
                put(1)
            })
            put(JSONObject().apply {
                put("string", "value")
            })
        })

        val valueList = value.getDataList()!!
        assertTrue(value.isDataList())
        assertEquals("value", valueList.getString(0))
        assertEquals(10, valueList.getInt(1))
        assertEquals(100L, valueList.getLong(2))
        assertEquals(111.111, valueList.getDouble(3))
        assertEquals(true, valueList.getBoolean(4))
        assertEquals(1, valueList.getDataList(5)!!.getInt(0))
        assertEquals("value", valueList.getDataObject(6)!!.getString("string"))
    }

    @Test
    fun convert_JSONObject_Returns_DataItemDataObject() {
        val value = DataItem.convert(
            JSONObject().apply {
                put("string", "value")
                put("int", 10)
                put("long", 100L)
                put("double", 111.111)
                put("boolean", true)
                put("array", JSONArray().apply {
                    put(1)
                })
                put("object", JSONObject().apply {
                    put("string", "value")
                })
            }
        )

        val valueDataObject = value.getDataObject()!!

        assertTrue(value.isDataObject())
        assertEquals("value", valueDataObject.getString("string"))
        assertEquals(10, valueDataObject.getInt("int"))
        assertEquals(100L, valueDataObject.getLong("long"))
        assertEquals(111.111, valueDataObject.getDouble("double"))
        assertEquals(true, valueDataObject.getBoolean("boolean"))

        assertEquals(1, valueDataObject.getDataList("array")!!.getInt(0))
        assertEquals("value", valueDataObject.getDataObject("object")!!.getString("string"))
    }

    @Test
    fun lazy_LazilyParsesValue_WhenInstantiatedWithString() {
        val string = DataItem.lazy("\"value\"")
        assertEquals("value", string.value)

        val int = DataItem.lazy("1")
        assertEquals(1, int.value)

        val long = DataItem.lazy(Long.MAX_VALUE.toString())
        assertEquals(Long.MAX_VALUE, long.value)

        val double = DataItem.lazy("1.111")
        assertEquals(1.111, double.value)

        val boolTrue = DataItem.lazy("true")
        assertEquals(true, boolTrue.value)

        val boolFalse = DataItem.lazy("false")
        assertEquals(false, boolFalse.value)

        val nullValue = DataItem.lazy("null")
        assertSame(DataItem.NULL, nullValue)
        assertEquals(DataItem.NULL, nullValue)
        assertNull(nullValue.value)

        val list = DataItem.lazy("[true]")
        assertEquals(true, list.getDataList()!!.get(0)!!.value)

        val dataObject = DataItem.lazy("{\"key\":\"value\"}")
        assertEquals("value", dataObject.getDataObject()!!.get("key")!!.value)
    }

    @Test
    fun toString_String_ReturnsJsonFormattedString() {
        val result = stringItem.toString()
        val resultWithSpacesAndQuotes = DataItem.string("Some, longer; \"string\".").toString()

        assertEquals("\"string\"", result)
        assertEquals("\"Some, longer; \\\"string\\\".\"", resultWithSpacesAndQuotes)
    }

    @Test
    fun toString_Int_ReturnsJsonFormattedString() {
        val result = intItem.toString()

        assertEquals("10", result)
    }

    @Test
    fun toString_Long_ReturnsJsonFormattedString() {
        val result = longItem.toString()

        assertEquals("100", result)
    }

    @Test
    fun toString_Double_ReturnsJsonFormattedString() {
        val result = doubleItem.toString()

        assertEquals("111.111", result)
    }

    @Test
    fun toString_Boolean_ReturnsJsonFormattedString() {
        val falseResult = booleanFalse.toString()
        val trueResult = booleanTrue.toString()

        assertEquals("false", falseResult)
        assertEquals("true", trueResult)
    }

    @Test
    fun toString_Null_ReturnsJsonFormattedString() {
        val nullResult = DataItem.convert(null).toString()
        val nullConstantResult = DataItem.NULL.toString()

        assertEquals("null", nullResult)
        assertEquals("null", nullConstantResult)
    }

    @Test
    fun toString_DataList_ReturnsJsonFormattedString() {
        val result = DataItem.convert(DataList.create {
            add("string")
            add(2)
            add(false)
            add(DataItem.NULL)
        }).toString()

        assertEquals("[\"string\",2,false,null]", result)
    }

    @Test
    fun toString_DataObject_ReturnsJsonFormattedString() {
        val result = DataItem.convert(DataObject.create {
            put("string", "string")
            put("int", 2)
            put("boolean", false)
            put("null", DataItem.NULL)
        }).toString()

        assertEquals("{\"string\":\"string\",\"int\":2,\"boolean\":false,\"null\":null}", result)
    }
}