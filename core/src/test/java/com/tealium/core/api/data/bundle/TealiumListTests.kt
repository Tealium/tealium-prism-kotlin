package com.tealium.core.api.data.bundle

import com.tealium.tests.common.trimJson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI

@RunWith(RobolectricTestRunner::class)
class TealiumListTests {

    private val testStringList = TealiumList.create {
        add("string")
        add("string 2")
        add("string 3")
        add("string 4")
        add("string 5")
    }

    private val testComplexList = TealiumList.create {
        add("string")
        add(1)
        add(Long.MAX_VALUE)
        add(111.111)
        add(false)
        add(TealiumList.create {
            add("string")
            add(1)
            add(Long.MAX_VALUE)
            add(111.111)
            add(false)
        })
        add(TealiumBundle.create {
            put("string", "string")
            put("int", 1)
            put("long", Long.MAX_VALUE)
            put("double", 111.111)
            put("false", false)
            put("list", TealiumList.create {
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

        assertSame(TealiumList.EMPTY_LIST, copy)
    }

    @Test
    fun create_Should_ReturnEmptyList_IfEmpty() {
        val list1 = TealiumList.create { }
        val list2 = TealiumList.Builder().getList()

        assertSame(TealiumList.EMPTY_LIST, list1)
        assertSame(TealiumList.EMPTY_LIST, list2)
    }

    @Test
    fun create_ConvertsString_ToTealiumValue() {
        val list = TealiumList.create {
            add("string")
        }

        assertEquals("string", list.getString(0))
    }

    @Test
    fun create_ConvertsInt_ToTealiumValue() {
        val list = TealiumList.create {
            add(10)
        }

        assertEquals(10, list.getInt(0))
    }

    @Test
    fun create_ConvertsLong_ToTealiumValue() {
        val list = TealiumList.create {
            add(1L)
            add(Long.MAX_VALUE)
        }

        assertEquals(1L, list.getLong(0))
        assertEquals(Long.MAX_VALUE, list.getLong(1))
    }

    @Test
    fun create_ConvertsDouble_ToTealiumValue() {
        val list = TealiumList.create {
            add(1.1)
            add(PI)
        }

        assertEquals(1.1, list.getDouble(0))
        assertEquals(PI, list.getDouble(1))
    }

    @Test
    fun create_ConvertsBoolean_ToTealiumValue() {
        val list = TealiumList.create {
            add(true)
            add(false)
        }

        assertEquals(true, list.getBoolean(0))
        assertEquals(false, list.getBoolean(1))
    }

    @Test
    fun create_ConvertsList_ToTealiumValue() {
        val list = TealiumList.create {
            add(TealiumList.create {
                add("string")
                add(1)
                add(1.1)
                add(1L)
                add(true)
            })
        }

        val subList = list.getList(0)!!
        assertEquals("string", subList.getString(0))
        assertEquals(1, subList.getInt(1))
        assertEquals(1.1, subList.getDouble(2))
        assertEquals(1L, subList.getLong(3))
        assertEquals(true, subList.getBoolean(4))
    }

    @Test
    fun create_ConvertsBundle_ToTealiumValue() {
        val list = TealiumList.create {
            add(TealiumBundle.create {
                put("string", "string")
                put("int", 1)
                put("long", Long.MAX_VALUE)
                put("double", 111.111)
                put("false", false)
            })
        }

        val childBundle = list.getBundle(0)!!
        assertEquals("string", childBundle.getString("string"))
        assertEquals(1, childBundle.getInt("int"))
        assertEquals(111.111, childBundle.getDouble("double"))
        assertEquals(Long.MAX_VALUE, childBundle.getLong("long"))
        assertEquals(false, childBundle.getBoolean("false"))
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
        val numbers = TealiumList.create {
            add(1)
            add(2)
            add(3)
        }
        val list = TealiumList.create {
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
        val lazyList = TealiumList.lazy(
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
        val lazyList = TealiumList.lazy(jsonString)

        assertSame(jsonString, lazyList.toString())
        assertEquals(jsonString, lazyList.toString())
    }

    @Test
    fun lazy_Invalidates_StringValue_IfValueRequested_And_InvalidString() {
        val jsonString = """
            [
                "string" : "string"
        """.trimJson()
        val lazyList = TealiumList.lazy(jsonString)
        val value = lazyList.getString(0)

        assertEquals("[]", lazyList.toString())
        assertNull(value)
    }

    @Test
    fun copy_OnInvalidLazy_ResultsInEmptyList() {
        val lazyList = TealiumList.lazy(
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
        val list = TealiumList.create {
            addAll(TealiumList.create {
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
        val list = TealiumList.create {
            add(1)
            addAll(TealiumList.create {
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
    fun add_Double_InfinityOrNan_Returns_Null() {
        val list = TealiumList.create {
            add(Double.NaN)
            add(Double.POSITIVE_INFINITY)
            add(Double.NEGATIVE_INFINITY)
        }

        assertEquals(null, list.getDouble(0))
        assertEquals(null, list.getDouble(1))
        assertEquals(null, list.getDouble(2))
        list.map {
            assertSame(TealiumValue.NULL, it)
        }
        assertEquals(
            """
            [ null, null, null ]
        """.trimJson(), list.toString()
        )
    }

    @Test
    fun add_Serializable_Adds_TealiumList() {
        val listSerializable = TestListSerializable("value", 10)

        val list = TealiumList.create {
            add(listSerializable)
        }

        val deserialized = list.get(0, TestListSerializable.Deserializer)

        assertEquals(listSerializable, deserialized)
    }
}