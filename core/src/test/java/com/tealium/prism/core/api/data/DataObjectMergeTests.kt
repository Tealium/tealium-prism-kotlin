package com.tealium.prism.core.api.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataObjectMergeTests {

    @Test
    fun merge_Takes_Keys_And_Values_From_Both_Objects_When_No_Key_Clashes() {
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

        val merged1 = lhs.merge(rhs)
        val merged2 = rhs.merge(lhs)

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
    fun merge_Merges_All_Keys_And_Values_From_Sub_Objects_When_No_Levels_Provided() {
        val lhs = DataObject.create {
            put("object", DataObject.create {
                put("substring_1", "string")
            })
        }

        val rhs = DataObject.create {
            put("object", DataObject.create {
                put("substring_2", "string")
            })
        }

        val merged1 = lhs.merge(rhs)
        val merged2 = rhs.merge(lhs)

        assertEquals(merged1, merged2)
        listOf(merged1, merged2).forEach {
            val obj = it.getDataObject("object")!!
            assertEquals(
                lhs.getDataObject("object")!!.getString("substring_1"),
                obj.getString("substring_1")
            )
            assertEquals(
                rhs.getDataObject("object")!!.getString("substring_2"),
                obj.getString("substring_2")
            )
        }
    }

    @Test
    fun merge_Prefers_Other_Object_Keys_And_Values_From_Sub_Objects() {
        val lhs = DataObject.create {
            put("object", DataObject.create {
                put("substring_1", "string")
            })
        }

        val rhs = DataObject.create {
            put("object", DataObject.create {
                put("substring_1", "updated")
                put("substring_2", "string")
            })
        }

        val merged1 = lhs.merge(rhs)

        val obj = merged1.getDataObject("object")!!
        val rhsObj = rhs.getDataObject("object")!!
        assertEquals(
            rhsObj.getString("substring_1"),
            obj.getString("substring_1")
        )
        assertEquals(
            rhsObj.getString("substring_2"),
            obj.getString("substring_2")
        )
    }

    @Test
    fun merge_Prefers_Other_Sub_Object_Values_After_Given_Levels() {
        val lhs = DataObject.create {
            put("lvl_1", DataObject.create {
                put("lvl_2", DataObject.create {
                    put("lvl_3", DataObject.create {
                        put("string_3", "string_3_1")
                    })
                })
            })
        }

        val rhs = DataObject.create {
            put("lvl_1", DataObject.create {
                put("lvl_2", DataObject.create {
                    put("lvl_3", DataObject.create {
                        put("substring_3", "string_3_2")
                    })
                })
            })
        }

        val merged = lhs.merge(rhs, 2)

        val lvl3 = merged.getDataObject("lvl_1")!!
            .getDataObject("lvl_2")!!
            .getDataObject("lvl_3")!!
        assertNull(lvl3.getString("string_3"))
        assertEquals(
            rhs.getDataObject("lvl_1")!!.getDataObject("lvl_2")!!.getDataObject("lvl_3"),
            lvl3
        )
    }

    @Test
    fun merge_Performs_Deep_Merge_When_Multiple_Object_Nesting() {
        val lhs = DataObject.create {
            put("key1", "string")
            put("key2", true)
            put("lvl-1", DataObject.create {
                put("key1", "string")
                put("key2", true)
                put("lvl-2", DataObject.create {
                    put("key1", "string")
                    put("key2", true)
                    put("lvl-3", DataObject.create {
                        put("key1", "string")
                        put("key2", true)
                    })
                })
            })
        }

        val rhs = DataObject.create {
            put("key1", "new string")
            put("key3", 1)
            put("lvl-1", DataObject.create {
                put("key1", "new string")
                put("key3", 1)
                put("lvl-2", DataObject.create {
                    put("key1", "new string")
                    put("key3", 1)
                    put("lvl-3", DataObject.create {
                        put("key1", "new string")
                        put("key3", 1)
                    })
                })
            })
        }

        val merged = lhs.merge(rhs)
        val lvl1 = merged.getDataObject("lvl-1")!!
        val lvl2 = lvl1.getDataObject("lvl-2")!!
        val lvl3 = lvl2.getDataObject("lvl-3")!!

        listOf(merged, lvl1, lvl2, lvl3).forEachIndexed { index, dataObject ->
            assertEquals( "new string", dataObject.getString("key1"))   // from rhs
            assertTrue(dataObject.getBoolean("key2")!!)                          // from lhs
            assertEquals( 1, dataObject.getInt("key3"))                 // from rhs
        }
    }

    @Test
    fun merge_Takes_Only_Root_Keys_From_Both_Objects_When_Levels_Less_Than_Zero() {
        val lhs = DataObject.create {
            put("string", "value")
            put("long", 100L)
            put("object", DataObject.create {
                put("string", "string")
                put("extra_string", "string")
            })
        }

        val rhs = DataObject.create {
            put("int", 10)
            put("double", 1.1)
            put("object", DataObject.create {
                put("string", "new string")
            })
        }

        val merged1 = lhs.merge(rhs, 0)
        val merged2 = lhs.merge(rhs, -1)

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
    fun merge_Does_Not_Merge_Lists() {
        val lhs = DataObject.create {
            put("list", DataList.create {
                add("string_1")
                add(10)
            })
        }

        val rhs = DataObject.create {
            put("list", DataList.create {
                add("string_2")
                add(20)
            })
        }

        val merged = lhs.merge(rhs)

        assertNotEquals(lhs.get("list"), merged.get("list"))
        assertEquals(rhs.get("list"), merged.get("list"))
    }

    @Test
    fun merge_Replaces_With_Same_DataObject_When_Existing_Value_Is_Not_A_DataObject() {
        val obj = DataObject.create {
            put("key", "value")
        }
        val lhs = DataObject.create {
            put("object", "string")
        }

        val rhs = DataObject.create {
            put("object", obj)
        }

        val merged = lhs.merge(rhs)

        assertSame(obj, merged.getDataObject("object"))
    }

    @Test
    fun merge_Also_Merges_Null_Values() {
        val lhs = DataObject.create {
            put("key", "string")
            put("obj", DataObject.create {
                put("subkey", "value")
            })
        }

        val rhs = DataObject.create {
            put("key", DataItem.NULL)
            put("obj", DataObject.create {
                put("subkey", DataItem.NULL)
            })
        }

        val merged = lhs.merge(rhs)

        assertEquals(DataItem.NULL, merged.get("key"))
        assertEquals(DataItem.NULL, merged.getDataObject("obj")!!.get("subkey"))
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
}