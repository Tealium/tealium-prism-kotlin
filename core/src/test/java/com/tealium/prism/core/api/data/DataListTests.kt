package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.tests.common.trimJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI

@RunWith(RobolectricTestRunner::class)
class DataListTests {

    private val testStringList = DataList.create {
        add("string")
        add("string 2")
        add("string 3")
        add("string 4")
        add("string 5")
    }

    private val testComplexList = DataList.create {
        add("string")
        add(1)
        add(Long.MAX_VALUE)
        add(111.111)
        add(false)
        add(DataList.create {
            add("string")
            add(1)
            add(Long.MAX_VALUE)
            add(111.111)
            add(false)
        })
        add(DataObject.create {
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
        val copy = testStringList.copy { }

        assertNotSame(testStringList, copy)
    }

    @Test
    fun copy_AndAdd_Should_AppendAndNotThrow_WhenIndexOutOfBounds() {
        val copy = testStringList.copy {
            add("new_value", 10)
        }

        assertEquals(6, copy.size)
        assertEquals("new_value", copy.getString(5))
    }

    @Test
    fun copy_AndRemove_Should_RemoveFromNewInstance() {
        val copy = testStringList.copy {
            remove(0)
        }

        assertNotEquals("string", copy.getString(0))
        assertEquals("string 2", copy.getString(0))
    }

    @Test
    fun copy_AndRemove_ShouldNot_RemoveFromNewInstance_WhenOutOfBounds() {
        val copy = testStringList.copy {
            remove(10)
        }

        assertEquals(testStringList.size, copy.size)
        assertEquals(testStringList.toString(), copy.toString())
    }

    @Test
    fun copy_AndClear_Should_RemoveAllFromNewInstance() {
        val copy = testStringList.copy {
            clear()
        }

        assertNull(copy.getString(0))
        assertNull(copy.getString(1))
        assertNull(copy.getString(2))
        assertNull(copy.getString(3))
        assertNull(copy.getString(4))
    }

    @Test
    fun copy_AndClear_Should_ReturnEmptyList_IfEmpty() {
        val copy = testStringList.copy {
            clear()
        }

        assertSame(DataList.EMPTY_LIST, copy)
    }

    @Test
    fun create_Should_ReturnEmptyList_IfEmpty() {
        val list1 = DataList.create { }
        val list2 = DataList.Builder().build()

        assertSame(DataList.EMPTY_LIST, list1)
        assertSame(DataList.EMPTY_LIST, list2)
    }

    @Test
    fun create_ConvertsString_ToDataItem() {
        val list = DataList.create {
            add("string")
        }

        assertEquals("string", list.getString(0))
    }

    @Test
    fun create_ConvertsInt_ToDataItem() {
        val list = DataList.create {
            add(10)
        }

        assertEquals(10, list.getInt(0))
    }

    @Test
    fun create_ConvertsLong_ToDataItem() {
        val list = DataList.create {
            add(1L)
            add(Long.MAX_VALUE)
        }

        assertEquals(1L, list.getLong(0))
        assertEquals(Long.MAX_VALUE, list.getLong(1))
    }

    @Test
    fun create_ConvertsDouble_ToDataItem() {
        val list = DataList.create {
            add(1.1)
            add(PI)
        }

        assertEquals(1.1, list.getDouble(0))
        assertEquals(PI, list.getDouble(1))
    }

    @Test
    fun create_ConvertsBoolean_ToDataItem() {
        val list = DataList.create {
            add(true)
            add(false)
        }

        assertEquals(true, list.getBoolean(0))
        assertEquals(false, list.getBoolean(1))
    }

    @Test
    fun create_ConvertsList_ToDataItem() {
        val list = DataList.create {
            add(DataList.create {
                add("string")
                add(1)
                add(1.1)
                add(1L)
                add(true)
            })
        }

        val subList = list.getDataList(0)!!
        assertEquals("string", subList.getString(0))
        assertEquals(1, subList.getInt(1))
        assertEquals(1.1, subList.getDouble(2))
        assertEquals(1L, subList.getLong(3))
        assertEquals(true, subList.getBoolean(4))
    }

    @Test
    fun create_ConvertsDataObject_ToDataItem() {
        val list = DataList.create {
            add(DataObject.create {
                put("string", "string")
                put("int", 1)
                put("long", Long.MAX_VALUE)
                put("double", 111.111)
                put("false", false)
            })
        }

        val childDataObject = list.getDataObject(0)!!
        assertEquals("string", childDataObject.getString("string"))
        assertEquals(1, childDataObject.getInt("int"))
        assertEquals(111.111, childDataObject.getDouble("double"))
        assertEquals(Long.MAX_VALUE, childDataObject.getLong("long"))
        assertEquals(false, childDataObject.getBoolean("false"))
    }

    @Test
    fun toString_Serializes_Simplelist_ToJson() {
        assertEquals(
            """
            [
                "string", 
                "string 2",
                "string 3",
                "string 4",
                "string 5"
            ]
        """.trimJson(), testStringList.toString()
        )
    }

    @Test
    fun toString_Serializes_NestedLists_ToJson() {
        val numbers = DataList.create {
            add(1)
            add(2)
            add(3)
        }
        val list = DataList.create {
            add(numbers)
            add(numbers)
            add(numbers)
        }

        assertEquals(
            """
            [
                [1,2,3],
                [1,2,3],
                [1,2,3]
            ]
        """.trimJson(), list.toString()
        )
    }

    @Test
    fun toString_Serializes_ComplexList_ToJson() {
        val string = testComplexList.toString()

        assertEquals(
            """
            [
                "string",
                1,
                ${Long.MAX_VALUE},
                111.111,
                false,
                [
                    "string",
                    1,
                    ${Long.MAX_VALUE},
                    111.111,
                    false
                ],
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
                    ]
                }
            ]
        """.trimJson(), string
        )
    }

    @Test
    fun lazy_OnlyParses_WhenDataIsRequested() {
        val lazyList = DataList.lazy(
            """
            [
                "string"
            ]
        """.trimJson()
        )

        assertEquals("string", lazyList.getString(0))
    }

    @Test
    fun lazy_ReturnsOriginal_StringValue_OnToString() {
        val jsonString = """
            [
                "string"
            ]
        """.trimJson()
        val lazyList = DataList.lazy(jsonString)

        assertSame(jsonString, lazyList.toString())
        assertEquals(jsonString, lazyList.toString())
    }

    @Test
    fun lazy_Invalidates_StringValue_IfValueRequested_And_InvalidString() {
        val jsonString = """
            [
                "string" : "string"
        """.trimJson()
        val lazyList = DataList.lazy(jsonString)
        val value = lazyList.getString(0)

        assertEquals("[]", lazyList.toString())
        assertNull(value)
    }

    @Test
    fun copy_OnInvalidLazy_ResultsInEmptyList() {
        val lazyList = DataList.lazy(
            """
            {
                "string" : "string"
        """.trimJson()
        )

        assertEquals(0, lazyList.size)
    }

    @Test
    fun size_Returns_CorrectItemCount() {
        val addedTo = testStringList.copy {
            add("new string")
        }
        val removedFrom = testStringList.copy {
            remove(0)
        }

        assertEquals(5, testStringList.size)
        assertEquals(6, addedTo.size)
        assertEquals(4, removedFrom.size)
    }

    @Test
    fun addAll_AddsAllEntries_FromGivenList() {
        val list = DataList.create {
            addAll(DataList.create {
                add(1)
                add(2)
                add(3)
            })
        }

        assertEquals(3, list.size)
        assertEquals(1, list.getInt(0))
        assertEquals(2, list.getInt(1))
        assertEquals(3, list.getInt(2))
    }

    @Test
    fun addAll_AddsAllEntries_FromGivenList_AtTheGivenIndex() {
        val list = DataList.create {
            add(1)
            addAll(DataList.create {
                add(2)
                add(3)
                add(4)
            }, 0)
        }

        assertEquals(4, list.size)
        assertEquals(2, list.getInt(0))
        assertEquals(3, list.getInt(1))
        assertEquals(4, list.getInt(2))
        assertEquals(1, list.getInt(3))
    }

    @Test
    fun add_Double_InfinityOrNan_Returns_String() {
        val list = DataList.create {
            add(Double.NaN)
            add(Double.POSITIVE_INFINITY)
            add(Double.NEGATIVE_INFINITY)
        }

        assertEquals("NaN", list.getString(0))
        assertEquals("Infinity", list.getString(1))
        assertEquals("-Infinity", list.getString(2))
        assertEquals(
            """
            [ "NaN", "Infinity", "-Infinity" ]
        """.trimJson(), list.toString()
        )
    }

    @Test
    fun add_Convertible_Adds_DataList() {
        val listConvertible = TestDataListConvertible("value", 10)

        val list = DataList.create {
            add(listConvertible)
        }

        val converted = list.get(0, TestDataListConvertible.Converter)

        assertEquals(listConvertible, converted)
    }

    @Test
    fun fromCollection_Returns_Valid_List_When_All_Types_Are_Valid() {
        val testList = listOf(
            1, 2L, 3.3, 4.4f, "String", true, listOf(1, 2, 3), mapOf("key" to "value")
        )
        val lists = listOf(DataList.fromCollection(testList), testList.asDataList())

        for (list in lists) {
            assertNotNull(list)
            assertEquals(1, list.getInt(0))
            assertEquals(2L, list.getLong(1))
            assertEquals(3.3, list.getDouble(2))
            assertEquals(4.4, list.getDouble(3)!!, 0.1)
            assertEquals("String", list.getString(4))
            assertEquals(true, list.getBoolean(5))
            assertEquals(DataList.create {
                add(1)
                add(2)
                add(3)
            }, list.getDataList(6))
            assertEquals(DataObject.create {
                put("key", "value")
            }, list.getDataObject(7))
        }
    }

    @Test(expected = UnsupportedDataItemException::class)
    fun fromCollection_Throws_When_Unsupported_Type() {
        val list = listOf(Any())

        DataList.fromCollection(list)
    }

    @Test(expected = UnsupportedDataItemException::class)
    fun asDataList_Throws_When_Unsupported_Type() {
        listOf(Any()).asDataList()
    }

    @Test
    fun fromStringCollection_Returns_Valid_List_Of_Strings() {
        val testList = listOf("test", "test2")
        val lists =
            listOf(DataList.fromStringCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals("test", list.getString(0))
            assertEquals("test2", list.getString(1))
        }
    }

    @Test
    fun fromStringCollection_Returns_Returns_Nulls_When_Null() {
        val testList = listOf("test", null, "test")
        val lists =
            listOf(DataList.fromStringCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals("test", list.getString(0))
            assertNull(list.getString(1))
            assertSame(DataItem.NULL, list.get(1))
            assertEquals("test", list.getString(2))
        }
    }

    @Test
    fun fromIntCollection_Returns_Valid_List_Of_Ints() {
        val testList = listOf(1, 2)
        val lists = listOf(DataList.fromIntCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals(1, list.getInt(0))
            assertEquals(2, list.getInt(1))
        }
    }

    @Test
    fun fromIntCollection_Returns_Nulls_When_Null() {
        val testList = listOf(1, null, 2)
        val lists = listOf(DataList.fromIntCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals(1, list.getInt(0))
            assertNull(list.getInt(1))
            assertSame(DataItem.NULL, list.get(1))
            assertEquals(2, list.getInt(2))
        }
    }

    @Test
    fun fromLongCollection_Returns_Valid_List_Of_Longs() {
        val testList = listOf(1L, 2L)
        val lists = listOf(DataList.fromLongCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals(1L, list.getLong(0))
            assertEquals(2L, list.getLong(1))
        }
    }

    @Test
    fun fromLongCollection_Returns_Nulls_When_Null() {
        val testList = listOf(1L, null, 2L)
        val lists = listOf(DataList.fromLongCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals(1L, list.getLong(0))
            assertNull(list.getLong(1))
            assertSame(DataItem.NULL, list.get(1))
            assertEquals(2L, list.getLong(2))
        }
    }

    @Test
    fun fromDoubleCollection_Returns_Valid_List_Of_Doubles() {
        val testList = listOf(1.1, 2.2)
        val lists = listOf(DataList.fromDoubleCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals(1.1, list.getDouble(0))
            assertEquals(2.2, list.getDouble(1))
        }
    }

    @Test
    fun fromDoubleCollection_Returns_Nulls_When_Null() {
        val testList = listOf(1.1, null, 2.2)
        val lists = listOf(DataList.fromDoubleCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals(1.1, list.getDouble(0))
            assertNull(list.getDouble(1))
            assertSame(DataItem.NULL, list.get(1))
            assertEquals(2.2, list.getDouble(2))
        }
    }

    @Test
    fun fromDoubleCollection_Returns_Strings_When_Infinite_Or_NaN() {
        val testList = listOf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)
        val lists = listOf(DataList.fromDoubleCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals("Infinity", list.getString(0))
            assertEquals("-Infinity", list.getString(1))
            assertEquals("NaN", list.getString(2))
            assertNull(list.getDouble(0))
            assertNull(list.getDouble(1))
            assertNull(list.getDouble(2))
        }
    }

    @Test
    fun fromBooleanCollection_Returns_Valid_List_Of_Booleans() {
        val testList = listOf(true, false)
        val lists = listOf(DataList.fromBooleanCollection(testList), testList.asDataList())

        for (list in lists) {
            assertTrue(list.getBoolean(0)!!)
            assertFalse(list.getBoolean(1)!!)
        }
    }

    @Test
    fun fromBooleanCollection_Returns_Nulls_When_Null() {
        val testList = listOf(true, null, false)
        val lists = listOf(DataList.fromBooleanCollection(testList), testList.asDataList())

        for (list in lists) {
            assertTrue(list.getBoolean(0)!!)
            assertNull(list.getBoolean(1))
            assertSame(DataItem.NULL, list.get(1))
            assertFalse(list.getBoolean(2)!!)
        }
    }

    @Test
    fun fromDataItemCollection_Returns_Valid_List_Of_DataItems() {
        val testList = listOf(DataItem.string("string"), DataItem.int(1))
        val lists =
            listOf(DataList.fromDataItemCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals("string", list.getString(0))
            assertEquals(1, list.getInt(1))
        }
    }

    @Test
    fun fromDataItemCollection_Returns_Nulls_When_Null() {
        val testList = listOf(DataItem.string("string"), null, DataItem.int(1))
        val lists =
            listOf(DataList.fromDataItemCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals("string", list.getString(0))
            assertNull(list.get(1)!!.value)
            assertSame(DataItem.NULL, list.get(1))
            assertEquals(1, list.getInt(2))
        }
    }

    @Test
    fun fromDataListCollection_Returns_Valid_List_Of_DataLists() {
        val testList = listOf(DataList.EMPTY_LIST, DataList.create {
            add(1)
        })
        val lists =
            listOf(DataList.fromDataListCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals(DataList.EMPTY_LIST, list.getDataList(0))
            assertEquals(1, list.getDataList(1)!!.getInt(0))
        }
    }

    @Test
    fun fromDataListCollection_Returns_Nulls_When_Null() {
        val testList = listOf(DataList.EMPTY_LIST, null, DataList.create {
            add(1)
        })
        val lists =
            listOf(DataList.fromDataListCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals(DataList.EMPTY_LIST, list.getDataList(0))
            assertNull(list.get(1)!!.value)
            assertSame(DataItem.NULL, list.get(1))
            assertEquals(1, list.getDataList(2)!!.getInt(0))
        }
    }

    @Test
    fun fromDataObjectCollection_Returns_Valid_List_Of_DataObjects() {
        val testList = listOf(DataObject.EMPTY_OBJECT, DataObject.create {
            put("key", 1)
        })
        val lists =
            listOf(DataList.fromDataObjectCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals(DataObject.EMPTY_OBJECT, list.getDataObject(0))
            assertEquals(1, list.getDataObject(1)!!.getInt("key"))
        }
    }

    @Test
    fun fromDataObjectCollection_Returns_Nulls_When_Null() {
        val testList = listOf(DataObject.EMPTY_OBJECT, null, DataObject.create {
            put("key", 1)
        })
        val lists =
            listOf(DataList.fromDataObjectCollection(testList), testList.asDataList())

        for (list in lists) {
            assertEquals(DataObject.EMPTY_OBJECT, list.getDataObject(0))
            assertNull(list.get(1)!!.value)
            assertSame(DataItem.NULL, list.get(1))
            assertEquals(1, list.getDataObject(2)!!.getInt("key"))
        }
    }
}