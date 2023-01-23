package com.tealium.core

import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.data.bundle.TealiumDeserializable
import com.tealium.core.api.data.bundle.TealiumList
import com.tealium.core.api.data.bundle.TealiumSerializable
import com.tealium.core.api.data.bundle.TealiumValue
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        val complexSerializable = ComplexSerializable("test", listOf("1", "2", "3"), mapOf("string" to "string"))
        val bundle = TealiumBundle.Builder()
            .putSerializable("ser", complexSerializable)
            .getBundle()

        val deserialized: ComplexSerializable? = bundle.get("ser", ComplexSerializable.Creator)
        assertEquals(complexSerializable.string, deserialized?.string)
        assertEquals(complexSerializable.list, deserialized?.list)
        assertEquals(complexSerializable.map, deserialized?.map)


        val newBundle: TealiumBundle = TealiumBundle.create {
            put("int", arrayOf<Int>(1, 2, 3))
        }
    }

    @Test
    fun testBundleCopy() {
        val bundle = TealiumBundle.Builder()
            .put("string", "string")
            .put("int", 1)
            .getBundle()

        val copy = bundle.copy {
            put("string", "new_value")
        }

        assertNotSame(bundle, copy)
        assertNotEquals("new_value", bundle.getString("string"))
        assertEquals("new_value", copy.getString("string"))
    }

    @Test
    fun testListCopy() {
        val list = TealiumList.Builder()
            .add("string")
            .add(1)
            .getList()

        val copy = list.copy {
            add("new_value")
        }

        assertNotSame(list, copy)
        assertEquals(2, list.size())
        assertEquals(3, copy.size())

        assertEquals("string", list.getString(0))
        assertEquals(1, list.getInt(1))
        assertNull(list.getString(2))

        assertEquals("string", copy.getString(0))
        assertEquals(1, copy.getInt(1))
        assertEquals("new_value", copy.getString(2))
    }


    class ComplexSerializable(
        val string: String,
        val list: List<String>,
        val map: Map<String, String>
    ) : TealiumSerializable {

        override fun serialize(): TealiumValue {
            val bundle = TealiumBundle.Builder()
                .put(KEY_STRING, string)
                .putAny(KEY_LIST, list)
                .putAny(KEY_MAP, map)
                .getBundle()
            return TealiumValue.convert(bundle)
        }

        companion object Creator : TealiumDeserializable<ComplexSerializable> {
            const val KEY_STRING = "string"
            const val KEY_LIST = "list"
            const val KEY_MAP = "map"

            override fun deserialize(
                value: TealiumValue,
            ): ComplexSerializable? {
                if (!value.isBundle()) return null

                return value.getBundle()?.let { bundle ->
                    val string = bundle.get(KEY_STRING)?.getString()
                    val list =
                        bundle.get(KEY_LIST)?.getList()?.map { it.value.toString() }
                    val map = bundle.get(KEY_MAP)?.getBundle()?.getAll()?.mapValues {
                        it.value.getString()!!
                    }
                    if (string != null && list != null && map != null) {
                        ComplexSerializable(
                            string, list, map
                        )
                    } else null
                }
            }
        }
    }
}