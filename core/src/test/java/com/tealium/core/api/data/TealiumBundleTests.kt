package com.tealium.core.api.data

import com.tealium.tests.common.trimJson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.PI

@RunWith(RobolectricTestRunner::class)
class TealiumBundleTests {

    private val testSimpleBundle = TealiumBundle.create {
        put("string", "string")
        put("int", 1)
        put("long", Long.MAX_VALUE)
        put("double", 111.111)
        put("false", false)
    }

    private val testComplexBundle = testSimpleBundle.copy {
        put("list", TealiumList.create {
            add("string")
            add(1)
            add(Long.MAX_VALUE)
            add(111.111)
            add(false)
        })
        put("child-bundle", TealiumBundle.create {
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
        val copy = testSimpleBundle.copy { }

        assertNotSame(testSimpleBundle, copy)
    }

    @Test
    fun copy_AndPut_Should_OverwriteExisting() {
        val copy = testSimpleBundle.copy {
            put("string", "new_value")
        }

        assertEquals("string", testSimpleBundle.getString("string"))
        assertEquals("new_value", copy.getString("string"))
    }

    @Test
    fun copy_AndRemove_Should_RemoveFromNewInstance() {
        val copy = testSimpleBundle.copy {
            remove("string")
        }

        assertNull(copy.getString("string"))
        assertNotNull(copy.getInt("int"))
    }

    @Test
    fun copy_AndClear_Should_RemoveAllFromNewInstance() {
        val copy = testSimpleBundle.copy {
            clear()
        }

        assertNull(copy.getString("string"))
        assertNull(copy.getInt("int"))
        assertNull(copy.getLong("long"))
        assertNull(copy.getDouble("double"))
        assertNull(copy.getBoolean("false"))
    }

    @Test
    fun copy_AndClear_Should_ReturnEmptyBundle_IfEmpty() {
        val copy = testSimpleBundle.copy {
            clear()
        }

        assertSame(TealiumBundle.EMPTY_BUNDLE, copy)
    }

    @Test
    fun create_Should_ReturnEmptyBundle_IfEmpty() {
        val bundle1 = TealiumBundle.create {  }
        val bundle2 = TealiumBundle.Builder().getBundle()

        assertSame(TealiumBundle.EMPTY_BUNDLE, bundle1)
        assertSame(TealiumBundle.EMPTY_BUNDLE, bundle2)
    }

    @Test
    fun create_ConvertsString_ToTealiumValue() {
        val bundle = TealiumBundle.create {
            put("string", "string")
        }

        assertEquals("string", bundle.getString("string"))
    }

    @Test
    fun create_ConvertsInt_ToTealiumValue() {
        val bundle = TealiumBundle.create {
            put("int", 10)
        }

        assertEquals(10, bundle.getInt("int"))
    }

    @Test
    fun create_ConvertsLong_ToTealiumValue() {
        val bundle = TealiumBundle.create {
            put("small-long", 1L)
            put("long", Long.MAX_VALUE)
        }

        assertEquals(1L, bundle.getLong("small-long"))
        assertEquals(Long.MAX_VALUE, bundle.getLong("long"))
    }

    @Test
    fun create_ConvertsDouble_ToTealiumValue() {
        val bundle = TealiumBundle.create {
            put("double", 1.1)
            put("pi", PI)
        }

        assertEquals(1.1, bundle.getDouble("double"))
        assertEquals(PI, bundle.getDouble("pi"))
    }

    @Test
    fun create_ConvertsDouble_InfinityOrNan_Returns_Null() {
        val bundle = TealiumBundle.create {
            put("nan", Double.NaN)
            put("plus-infinity", Double.POSITIVE_INFINITY)
            put("negative-infinity", Double.NEGATIVE_INFINITY)
        }

        assertEquals(null, bundle.getDouble("nan"))
        assertEquals(null, bundle.getDouble("plus-infinity"))
        assertEquals(null, bundle.getDouble("negative-infinity"))
        bundle.map {
            assertSame(TealiumValue.NULL, it.value)
        }
        assertEquals("""
            { 
                "nan": null,
                "plus-infinity": null,
                "negative-infinity": null 
            }
        """.trimJson(), bundle.toString())
    }

    @Test
    fun create_ConvertsBoolean_ToTealiumValue() {
        val bundle = TealiumBundle.create {
            put("true", true)
            put("false", false)
        }

        assertEquals(true, bundle.getBoolean("true"))
        assertEquals(false, bundle.getBoolean("false"))
    }

    @Test
    fun create_ConvertsList_ToTealiumValue() {
        val bundle = TealiumBundle.create {
            put("list", TealiumList.create {
                add("string")
                add(1)
                add(1.1)
                add(1L)
                add(true)
            })
        }

        val list = bundle.getList("list")!!
        assertEquals("string", list.getString(0))
        assertEquals(1, list.getInt(1))
        assertEquals(1.1, list.getDouble(2))
        assertEquals(1L, list.getLong(3))
        assertEquals(true, list.getBoolean(4))
    }

    @Test
    fun create_ConvertsBundle_ToTealiumValue() {
        val bundle = TealiumBundle.create {
            put("bundle", testSimpleBundle)
        }

        val childBundle = bundle.getBundle("bundle")!!
        assertEquals("string", childBundle.getString("string"))
        assertEquals(1, childBundle.getInt("int"))
        assertEquals(111.111, childBundle.getDouble("double"))
        assertEquals(Long.MAX_VALUE, childBundle.getLong("long"))
        assertEquals(false, childBundle.getBoolean("false"))
    }

    @Test
    fun create_ConvertsSerializable_ToBundle_AndBack() {
        val bundleSerializable = TestBundleSerializable("value", 10)

        val bundle = TealiumBundle.create {
            put("serialized", bundleSerializable)
        }

        val deserialized = bundle.get("serialized", TestBundleSerializable.Deserializer)

        assertEquals(bundleSerializable, deserialized)
    }

    @Test
    fun toString_Serializes_SimpleBundle_ToJson() {
        assertEquals(
            """
            {
                "string": "string",
                "int": 1,
                "long": ${Long.MAX_VALUE},
                "double": 111.111,
                "false": false
            }
        """.trimJson(), testSimpleBundle.toString()
        )
    }

    @Test
    fun toString_Serializes_NestedLists_ToJson() {
        val numbers = TealiumList.create {
            add(1)
            add(2)
            add(3)
        }
        val list = TealiumBundle.create {
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
    fun toString_Serializes_ComplexBundle_ToJson() {
        val string = testComplexBundle.toString()

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
                "child-bundle": {
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
        val lazyBundle = TealiumBundle.lazy("""
            {
                "string" : "string"
            }
        """.trimJson())

        assertEquals("string", lazyBundle.getString("string"))
    }

    @Test
    fun lazy_ReturnsOriginal_StringValue_OnToString() {
        val jsonString = """
            {
                "string" : "string"
            }
        """.trimJson()
        val lazyBundle = TealiumBundle.lazy(jsonString)

        assertSame(jsonString, lazyBundle.toString())
        assertEquals(jsonString, lazyBundle.toString())
    }

    @Test
    fun lazy_Invalidates_StringValue_IfValueRequested_And_InvalidString() {
        val jsonString = """
            {
                "string" : "string"
        """.trimJson()
        val lazyBundle = TealiumBundle.lazy(jsonString)
        val value = lazyBundle.getString("string")

        assertEquals("{}", lazyBundle.toString())
        assertNull(value)
    }

    @Test
    fun copy_OnInvalidLazy_ResultsInEmptyBundle() {
        val lazyBundle = TealiumBundle.lazy("""
            {
                "string" : "string"
        """.trimJson()).copy {  }

        assertEquals(0, lazyBundle.size)
    }

    @Test
    fun size_Returns_CorrectItemCount() {
        val addedTo = testSimpleBundle.copy {
            put("new_key", "new string")
        }
        val removedFrom = testSimpleBundle.copy {
            remove("string")
        }

        assertEquals(5, testSimpleBundle.size)
        assertEquals(6, addedTo.size)
        assertEquals(4, removedFrom.size)
    }

    @Test
    fun plus_WithNoClashes_ReturnsMergedTealiumBundle() {
        val lhs = TealiumBundle.create {
            put("string", "value")
            put("long", 100L)
            put("list", TealiumList.create {
                add("string")
                add(10)
            })
        }

        val rhs = TealiumBundle.create {
            put("int", 10)
            put("double", 1.1)
            put("bundle", TealiumBundle.create {
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
            assertEquals(rhs.get("bundle"), it.get("bundle"))
        }
    }

    @Test
    fun plus_WithClashes_PrefersIncoming() {
        val lhs = TealiumBundle.create {
            put("string", "value")
            put("long", 100L)
            put("list", TealiumList.create {
                add("string")
                add(10)
            })
        }

        val rhs = TealiumBundle.create {
            put("string", "new value")
            put("long", 1000L)
            put("list", TealiumList.create {
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
    fun plus_WithBundleClash_MergesChildBundleProperties() {
        val lhsChild = TealiumBundle.create {
            put("key1", "string")
            put("key2", true)
            put("key3", 10)
        }
        val lhs = TealiumBundle.create {
            put("bundle", lhsChild)
        }

        val rhsChild = TealiumBundle.create {
            put("key1", "new string")
            put("key4", "extra string")
        }
        val rhs = TealiumBundle.create {
            put("bundle", rhsChild)
        }

        val merged = lhs + rhs
        val bundle = merged.getBundle("bundle")!!

        assertNull(bundle.get("key2"))
        assertNull(bundle.get("key3"))
        assertEquals(rhsChild.get("key1"), bundle.get("key1"))
        assertEquals(rhsChild.get("key4"), bundle.get("key4"))
    }

    @Test
    fun plus_WithIncomingBundle_OverwritesNonBundleProperties() {
        val lhs = TealiumBundle.create {
            put("key1", "string")
            put("key2", 10)
            put("key3", true)
        }

        val rhsChild = TealiumBundle.create {
            put("key1", "new string")
            put("key4", "extra string")
        }
        val rhs = TealiumBundle.create {
            put("key1", rhsChild)
            put("key2", rhsChild)
            put("key3", rhsChild)
        }

        val merged = lhs + rhs

        assertEquals(rhsChild, merged.getBundle("key1"))
        assertEquals(rhsChild, merged.getBundle("key2"))
        assertEquals(rhsChild, merged.getBundle("key3"))
    }
}