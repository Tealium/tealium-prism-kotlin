package com.tealium.core.api.data

import com.tealium.core.api.data.DataItemUtils.asDataObject
import com.tealium.tests.common.trimJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI

@RunWith(RobolectricTestRunner::class)
class DataObjectTests {

    private val testSimpleDataObject = DataObject.create {
        put("string", "string")
        put("int", 1)
        put("long", Long.MAX_VALUE)
        put("double", 111.111)
        put("false", false)
    }

    private val testComplexDataObject = testSimpleDataObject.copy {
        put("list", DataList.create {
            add("string")
            add(1)
            add(Long.MAX_VALUE)
            add(111.111)
            add(false)
        })
        put("child-object", DataObject.create {
            put("string", "string")
            put("int", 1)
            put("long", Long.MAX_VALUE)
            put("double", 111.111)
            put("false", false)
            put("list", DataList.create {
                add("string")
                add(1)
                add(Long.MAX_VALUE)
                add(111.111)
                add(false)
            })
        })
    }

    @Test
    fun copy_Should_CreateNewInstance() {
        val copy = testSimpleDataObject.copy { }

        assertNotSame(testSimpleDataObject, copy)
    }

    @Test
    fun copy_AndPut_Should_OverwriteExisting() {
        val copy = testSimpleDataObject.copy {
            put("string", "new_value")
        }

        assertEquals("string", testSimpleDataObject.getString("string"))
        assertEquals("new_value", copy.getString("string"))
    }

    @Test
    fun copy_AndRemove_Should_RemoveFromNewInstance() {
        val copy = testSimpleDataObject.copy {
            remove("string")
        }

        assertNull(copy.getString("string"))
        assertNotNull(copy.getInt("int"))
    }

    @Test
    fun copy_AndClear_Should_RemoveAllFromNewInstance() {
        val copy = testSimpleDataObject.copy {
            clear()
        }

        assertNull(copy.getString("string"))
        assertNull(copy.getInt("int"))
        assertNull(copy.getLong("long"))
        assertNull(copy.getDouble("double"))
        assertNull(copy.getBoolean("false"))
    }

    @Test
    fun copy_AndClear_Should_ReturnEmptyDataObject_IfEmpty() {
        val copy = testSimpleDataObject.copy {
            clear()
        }

        assertSame(DataObject.EMPTY_OBJECT, copy)
    }

    @Test
    fun create_Should_ReturnEmptyDataObject_IfEmpty() {
        val dataObject1 = DataObject.create { }
        val dataObject2 = DataObject.Builder().build()

        assertSame(DataObject.EMPTY_OBJECT, dataObject1)
        assertSame(DataObject.EMPTY_OBJECT, dataObject2)
    }

    @Test
    fun create_ConvertsString_ToDataItem() {
        val dataObject = DataObject.create {
            put("string", "string")
        }

        assertEquals("string", dataObject.getString("string"))
    }

    @Test
    fun create_ConvertsInt_ToDataItem() {
        val dataObject = DataObject.create {
            put("int", 10)
        }

        assertEquals(10, dataObject.getInt("int"))
    }

    @Test
    fun create_ConvertsLong_ToDataItem() {
        val dataObject = DataObject.create {
            put("small-long", 1L)
            put("long", Long.MAX_VALUE)
        }

        assertEquals(1L, dataObject.getLong("small-long"))
        assertEquals(Long.MAX_VALUE, dataObject.getLong("long"))
    }

    @Test
    fun create_ConvertsDouble_ToDataItem() {
        val dataObject = DataObject.create {
            put("double", 1.1)
            put("pi", PI)
        }

        assertEquals(1.1, dataObject.getDouble("double"))
        assertEquals(PI, dataObject.getDouble("pi"))
    }

    fun create_ConvertsDouble_InfinityOrNan_Throws() {
        val dataObject = DataObject.create {
            put("nan", Double.NaN)
            put("plus-infinity", Double.POSITIVE_INFINITY)
            put("negative-infinity", Double.NEGATIVE_INFINITY)
        }

        assertEquals(null, dataObject.getDouble("nan"))
        assertEquals(null, dataObject.getDouble("plus-infinity"))
        assertEquals(null, dataObject.getDouble("negative-infinity"))
        dataObject.map {
            assertSame(DataItem.NULL, it.value)
        }
        assertEquals(
            """
            {
                "nan": null,
                "plus-infinity": null,
                "negative-infinity": null
            }
        """.trimJson(), dataObject.toString()
        )
    }

    @Test
    fun create_ConvertsBoolean_ToDataItem() {
        val dataObject = DataObject.create {
            put("true", true)
            put("false", false)
        }

        assertEquals(true, dataObject.getBoolean("true"))
        assertEquals(false, dataObject.getBoolean("false"))
    }

    @Test
    fun create_ConvertsList_ToDataItem() {
        val dataObject = DataObject.create {
            put("list", DataList.create {
                add("string")
                add(1)
                add(1.1)
                add(1L)
                add(true)
            })
        }

        val list = dataObject.getDataList("list")!!
        assertEquals("string", list.getString(0))
        assertEquals(1, list.getInt(1))
        assertEquals(1.1, list.getDouble(2))
        assertEquals(1L, list.getLong(3))
        assertEquals(true, list.getBoolean(4))
    }

    @Test
    fun create_ConvertsDataObject_ToDataItem() {
        val dataObject = DataObject.create {
            put("object", testSimpleDataObject)
        }

        val childDataObject = dataObject.getDataObject("object")!!
        assertEquals("string", childDataObject.getString("string"))
        assertEquals(1, childDataObject.getInt("int"))
        assertEquals(111.111, childDataObject.getDouble("double"))
        assertEquals(Long.MAX_VALUE, childDataObject.getLong("long"))
        assertEquals(false, childDataObject.getBoolean("false"))
    }

    @Test
    fun create_ConvertsConvertible_ToDataObject_AndBack() {
        val dataObjectConvertible = TestDataObjectConvertible("value", 10)

        val dataObject = DataObject.create {
            put("converted", dataObjectConvertible)
        }

        val converted = dataObject.get("converted", TestDataObjectConvertible.Converter)

        assertEquals(dataObjectConvertible, converted)
    }

    @Test
    fun toString_Serializes_SimpleDataObject_ToJson() {
        assertEquals(
            """
            {
                "string": "string",
                "int": 1,
                "long": ${Long.MAX_VALUE},
                "double": 111.111,
                "false": false
            }
        """.trimJson(), testSimpleDataObject.toString()
        )
    }

    @Test
    fun toString_Serializes_NestedLists_ToJson() {
        val numbers = DataList.create {
            add(1)
            add(2)
            add(3)
        }
        val list = DataObject.create {
            put("key1", numbers)
            put("key2", numbers)
            put("key3", numbers)
        }

        assertEquals(
            """
            {
                "key1": [1,2,3],
                "key2": [1,2,3],
                "key3": [1,2,3]
            }
        """.trimJson(), list.toString()
        )
    }

    @Test
    fun toString_Serializes_ComplexDataObject_ToJson() {
        val string = testComplexDataObject.toString()

        assertEquals(
            """
            {
                "string": "string",
                "int": 1,
                "long": ${Long.MAX_VALUE},
                "double": 111.111,
                "false": false,
                "list": [
                    "string",
                    1,
                    ${Long.MAX_VALUE},
                    111.111,
                    false
                ],
                "child-object": {
                    "string": "string",
                    "int": 1,
                    "long": ${Long.MAX_VALUE},
                    "double": 111.111,
                    "false": false,
                    "list": [
                        "string",
                        1,
                        ${Long.MAX_VALUE},
                        111.111,
                        false
                    ]
                }
            }
        """.trimJson(), string
        )
    }

    @Test
    fun lazy_OnlyParses_WhenDataIsRequested() {
        val lazyDataObject = DataObject.lazy(
            """
            {
                "string" : "string"
            }
        """.trimJson()
        )

        assertEquals("string", lazyDataObject.getString("string"))
    }

    @Test
    fun lazy_ReturnsOriginal_StringValue_OnToString() {
        val jsonString = """
            {
                "string" : "string"
            }
        """.trimJson()
        val lazyDataObject = DataObject.lazy(jsonString)

        assertSame(jsonString, lazyDataObject.toString())
        assertEquals(jsonString, lazyDataObject.toString())
    }

    @Test
    fun lazy_Invalidates_StringValue_IfValueRequested_And_InvalidString() {
        val jsonString = """
            {
                "string" : "string"
        """.trimJson()
        val lazyDataObject = DataObject.lazy(jsonString)
        val value = lazyDataObject.getString("string")

        assertEquals("{}", lazyDataObject.toString())
        assertNull(value)
    }

    @Test
    fun copy_OnInvalidLazy_ResultsInEmptyDataObject() {
        val lazyDataObject = DataObject.lazy(
            """
            {
                "string" : "string"
        """.trimJson()
        ).copy { }

        assertEquals(0, lazyDataObject.size)
    }

    @Test
    fun size_Returns_CorrectItemCount() {
        val addedTo = testSimpleDataObject.copy {
            put("new_key", "new string")
        }
        val removedFrom = testSimpleDataObject.copy {
            remove("string")
        }

        assertEquals(5, testSimpleDataObject.size)
        assertEquals(6, addedTo.size)
        assertEquals(4, removedFrom.size)
    }

    @Test
    fun plus_WithNoClashes_ReturnsMergedDataObject() {
        val lhs = DataObject.create {
            put("string", "value")
            put("long", 100L)
            put("list", DataList.create {
                add("string")
                add(10)
            })
        }

        val rhs = DataObject.create {
            put("int", 10)
            put("double", 1.1)
            put("object", DataObject.create {
                put("substring", "string")
            })
        }

        val merged1 = lhs + rhs
        val merged2 = rhs + lhs

        listOf(merged1, merged2).forEach {
            assertEquals(lhs.get("string"), it.get("string"))
            assertEquals(lhs.get("long"), it.get("long"))
            assertEquals(lhs.get("list"), it.get("list"))

            assertEquals(rhs.get("int"), it.get("int"))
            assertEquals(rhs.get("double"), it.get("double"))
            assertEquals(rhs.get("object"), it.get("object"))
        }
    }

    @Test
    fun plus_WithClashes_PrefersIncoming() {
        val lhs = DataObject.create {
            put("string", "value")
            put("long", 100L)
            put("list", DataList.create {
                add("string")
                add(10)
            })
        }

        val rhs = DataObject.create {
            put("string", "new value")
            put("long", 1000L)
            put("list", DataList.create {
                add("string2")
                add(100)
            })
        }

        val merged = lhs + rhs

        println(merged.get("list"))

        assertEquals(rhs.get("string"), merged.get("string"))
        assertEquals(rhs.get("long"), merged.get("long"))
        assertEquals(rhs.get("list"), merged.get("list"))
    }

    @Test
    fun plus_WithDataObjectClash_MergesChildDataObjectProperties() {
        val lhsChild = DataObject.create {
            put("key1", "string")
            put("key2", true)
            put("key3", 10)
        }
        val lhs = DataObject.create {
            put("object", lhsChild)
        }

        val rhsChild = DataObject.create {
            put("key1", "new string")
            put("key4", "extra string")
        }
        val rhs = DataObject.create {
            put("object", rhsChild)
        }

        val merged = lhs + rhs
        val dataObject = merged.getDataObject("object")!!

        assertNull(dataObject.get("key2"))
        assertNull(dataObject.get("key3"))
        assertEquals(rhsChild.get("key1"), dataObject.get("key1"))
        assertEquals(rhsChild.get("key4"), dataObject.get("key4"))
    }

    @Test
    fun plus_WithIncomingDataObject_OverwritesNonDataObjectProperties() {
        val lhs = DataObject.create {
            put("key1", "string")
            put("key2", 10)
            put("key3", true)
        }

        val rhsChild = DataObject.create {
            put("key1", "new string")
            put("key4", "extra string")
        }
        val rhs = DataObject.create {
            put("key1", rhsChild)
            put("key2", rhsChild)
            put("key3", rhsChild)
        }

        val merged = lhs + rhs

        assertEquals(rhsChild, merged.getDataObject("key1"))
        assertEquals(rhsChild, merged.getDataObject("key2"))
        assertEquals(rhsChild, merged.getDataObject("key3"))
    }

    @Test
    fun builder_Build_Should_Not_Update_Already_Built_DataObjects_When_Reused() {
        val builder = DataObject.Builder()
        val dataObject1 = builder.put("key", "value").build()
        val dataObject2 = builder.put("key", "new_value").build()

        assertEquals("value", dataObject1.getString("key"))
        assertEquals("new_value", dataObject2.getString("key"))
    }

    @Test
    fun fromString_Returns_Null_When_Invalid_Json() {
        val dataObject = DataObject.fromString("{\"key\" 10}")

        assertNull(dataObject)
    }

    @Test(expected = UnsupportedDataItemException::class)
    fun fromMap_Throws_When_Keys_Are_Not_String() {
        DataObject.fromMap(mapOf(1 to ""))
    }

    @Test
    fun fromMap_Returns_DataObject_When_All_Keys_And_Values_Are_Valid() {
        val testMap = mapOf(
            "key" to "string",
            "map" to mapOf(
                "sub-key" to 10
            ),
            "mixed-array" to arrayOf(1, "value", true),
            "null" to null
        )
        val dataObjects = listOf(DataObject.fromMap(testMap), testMap.asDataObject())

        for (dataObject in dataObjects) {
            assertEquals("string", dataObject.getString("key"))
            assertEquals(10, dataObject.getDataObject("map")?.getInt("sub-key"))
            assertEquals(1, dataObject.getDataList("mixed-array")?.getInt(0))
            assertEquals("value", dataObject.getDataList("mixed-array")?.getString(1))
            assertEquals(true, dataObject.getDataList("mixed-array")?.getBoolean(2))
            assertEquals(DataItem.NULL, dataObject.get("null"))
        }
    }

    @Test(expected = UnsupportedDataItemException::class)
    fun fromMap_Throws_When_Value_Is_Not_Supported() {
        DataObject.fromMap(mapOf("key" to Any()))
    }

    @Test
    fun fromMapOfStrings_Returns_Valid_DataObject_Of_Strings() {
        val testMap = mapOf(
            "key1" to "value1",
            "key2" to "value2",
            "null" to null
        )
        val dataObjects = listOf(DataObject.fromMapOfStrings(testMap), testMap.asDataObject())

        for (dataObject in dataObjects) {
            assertEquals("value1", dataObject.getString("key1"))
            assertEquals("value2", dataObject.getString("key2"))
            assertEquals(DataItem.NULL, dataObject.get("null"))
            assertNull(dataObject.getString("null"))
        }
    }

    @Test
    fun fromMapOfInts_Returns_Valid_DataObject_Of_Ints() {
        val testMap = mapOf(
            "key1" to 1,
            "key2" to 2,
            "null" to null
        )
        val dataObjects = listOf(DataObject.fromMapOfInts(testMap), testMap.asDataObject())

        for (dataObject in dataObjects) {
            assertEquals(1, dataObject.getInt("key1"))
            assertEquals(2, dataObject.getInt("key2"))
            assertEquals(DataItem.NULL, dataObject.get("null"))
            assertNull(dataObject.getInt("null"))
        }
    }

    @Test
    fun fromMapOfLongs_Returns_Valid_DataObject_Of_Longs() {
        val testMap = mapOf(
            "key1" to 1L,
            "key2" to 2L,
            "null" to null
        )
        val dataObjects = listOf(DataObject.fromMapOfLongs(testMap), testMap.asDataObject())

        for (dataObject in dataObjects) {
            assertEquals(1L, dataObject.getLong("key1"))
            assertEquals(2L, dataObject.getLong("key2"))
            assertEquals(DataItem.NULL, dataObject.get("null"))
            assertNull(dataObject.getLong("null"))
        }
    }

    @Test
    fun fromMapOfDoubles_Returns_Valid_DataObject_Of_Doubles() {
        val testMap = mapOf(
            "key1" to 1.1,
            "key2" to 2.2,
            "null" to null
        )
        val dataObjects = listOf(DataObject.fromMapOfDoubles(testMap), testMap.asDataObject())

        for (dataObject in dataObjects) {
            assertEquals(1.1, dataObject.getDouble("key1"))
            assertEquals(2.2, dataObject.getDouble("key2"))
            assertEquals(DataItem.NULL, dataObject.get("null"))
            assertNull(dataObject.getDouble("null"))
        }
    }

    @Test
    fun fromMapOfBooleans_Returns_Valid_DataObject_Of_Booleans() {
        val testMap = mapOf(
            "key1" to true,
            "key2" to false,
            "null" to null
        )
        val dataObjects = listOf(DataObject.fromMapOfBooleans(testMap), testMap.asDataObject())

        for (dataObject in dataObjects) {
            assertEquals(true, dataObject.getBoolean("key1"))
            assertEquals(false, dataObject.getBoolean("key2"))
            assertEquals(DataItem.NULL, dataObject.get("null"))
            assertNull(dataObject.getBoolean("null"))
        }
    }

    @Test
    fun fromMapOfStringCollections_Returns_Valid_DataObject_Of_String_Lists() {
        val testMap = mapOf(
            "key1" to listOf("1", "2"),
            "null" to listOf("3", null)
        )
        val dataObjects =
            listOf(DataObject.fromMapOfStringCollections(testMap), testMap.asDataObject())

        for (dataObject in dataObjects) {
            val list1 = dataObject.getDataList("key1")!!
            val list2 = dataObject.getDataList("null")!!
            assertEquals("1", list1.getString(0))
            assertEquals("2", list1.getString(1))
            assertEquals("3", list2.getString(0))
            assertEquals(DataItem.NULL, list2.get(1))
            assertNull(list2.getString(1))
        }
    }

    @Test
    fun fromMapOfIntCollections_Returns_Valid_DataObject_Of_Int_Lists() {
        val testMap = mapOf(
            "key1" to listOf(1, 2),
            "null" to listOf(3, null)
        )
        val dataObjects =
            listOf(DataObject.fromMapOfIntCollections(testMap), testMap.asDataObject())

        for (dataObject in dataObjects) {
            val list1 = dataObject.getDataList("key1")!!
            val list2 = dataObject.getDataList("null")!!
            assertEquals(1, list1.getInt(0))
            assertEquals(2, list1.getInt(1))
            assertEquals(3, list2.getInt(0))
            assertEquals(DataItem.NULL, list2.get(1))
            assertNull(list2.getInt(1))
        }
    }

    @Test
    fun fromMapOfLongCollections_Returns_Valid_DataObject_Of_Long_Lists() {
        val testMap = mapOf(
            "key1" to listOf(1L, 2L),
            "null" to listOf(3L, null)
        )
        val dataObjects =
            listOf(DataObject.fromMapOfLongCollections(testMap), testMap.asDataObject())

        for (dataObject in dataObjects) {
            val list1 = dataObject.getDataList("key1")!!
            val list2 = dataObject.getDataList("null")!!
            assertEquals(1L, list1.getLong(0))
            assertEquals(2L, list1.getLong(1))
            assertEquals(3L, list2.getLong(0))
            assertEquals(DataItem.NULL, list2.get(1))
            assertNull(list2.getLong(1))
        }
    }

    @Test
    fun fromMapOfDoubleCollections_Returns_Valid_DataObject_Of_Double_Lists() {
        val testMap = mapOf(
            "key1" to listOf(1.1, 2.2),
            "null" to listOf(3.3, null)
        )
        val dataObjects =
            listOf(DataObject.fromMapOfDoubleCollections(testMap), testMap.asDataObject())

        for (dataObject in dataObjects) {
            val list1 = dataObject.getDataList("key1")!!
            val list2 = dataObject.getDataList("null")!!
            assertEquals(1.1, list1.getDouble(0))
            assertEquals(2.2, list1.getDouble(1))
            assertEquals(3.3, list2.getDouble(0))
            assertEquals(DataItem.NULL, list2.get(1))
            assertNull(list2.getDouble(1))
        }
    }

    @Test
    fun fromMapOfDoubleCollections_Returns_Nulls_When_Invalid_Double_Lists() {
        val testMap = mapOf(
            "key1" to listOf(1.1, 2.2),
            "null" to listOf(Double.NaN, null)
        )
        val dataObjects =
            listOf(DataObject.fromMapOfDoubleCollections(testMap), testMap.asDataObject())

        for (dataObject in dataObjects) {
            val list1 = dataObject.getDataList("key1")!!
            val list2 = dataObject.getDataList("null")!!
            assertEquals(1.1, list1.getDouble(0))
            assertEquals(2.2, list1.getDouble(1))
            assertEquals(DataItem.NULL, list2.get(0))
            assertNull(list2.getDouble(0))
            assertEquals(DataItem.NULL, list2.get(1))
            assertNull(list2.getDouble(1))
        }
    }

    @Test
    fun fromMapOfBooleanCollections_Returns_Valid_DataObject_Of_Boolean_Lists() {
        val testMap = mapOf(
            "key1" to listOf(true, false),
            "null" to listOf(true, null)
        )
        val dataObjects =
            listOf(DataObject.fromMapOfBooleanCollections(testMap), testMap.asDataObject())

        for (dataObject in dataObjects) {
            val list1 = dataObject.getDataList("key1")!!
            val list2 = dataObject.getDataList("null")!!
            assertEquals(true, list1.getBoolean(0))
            assertEquals(false, list1.getBoolean(1))
            assertEquals(true, list2.getBoolean(0))
            assertEquals(DataItem.NULL, list2.get(1))
            assertNull(list2.getBoolean(1))
        }
    }
}