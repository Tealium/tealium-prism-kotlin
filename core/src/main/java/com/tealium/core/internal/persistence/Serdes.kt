package com.tealium.core.internal.persistence

import com.tealium.core.api.Deserializer
import com.tealium.core.api.Serde
import com.tealium.core.api.Serializer
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.data.bundle.TealiumList
import org.json.JSONArray
import org.json.JSONObject

object Serdes {
    private val serdeMap: MutableMap<Class<*>, Serde<*, String>> = mutableMapOf()

    private var stringSerde: Serde<String, String>? = null
    private var intSerde: Serde<Int, String>? = null
    private var longSerde: Serde<Long, String>? = null
    private var doubleSerde: Serde<Double, String>? = null
    private var booleanSerde: Serde<Boolean, String>? = null
//    private var stringArraySerde: Serde<Array<String>, String>? = null
//    private var intArraySerde: Serde<Array<Int>, String>? = null
//    private var longArraySerde: Serde<Array<Long>, String>? = null
//    private var doubleArraySerde: Serde<Array<Double>, String>? = null
//    private var booleanArraySerde: Serde<Array<Boolean>, String>? = null
//    private var jsonObjectSerde: Serde<JSONObject, String>? = null
//    private var jsonArraySerde: Serde<JSONArray, String>? = null

    private var tealiumListSerde: Serde<TealiumList, String>? = null
    private var tealiumBundleSerde: Serde<TealiumBundle, String>? = null

    fun stringSerde(): Serde<String, String> {
        return (stringSerde
            ?: StringSerde()).also {
            stringSerde = it
            serdeMap[String::class.java] = it
        }
    }
    fun intSerde(): Serde<Int, String> {
        return (intSerde
            ?: IntSerde()).also {
            intSerde = it
            serdeMap[Int::class.java] = it
        }
    }
    fun longSerde(): Serde<Long, String> {
        return (longSerde
            ?: LongSerde()).also {
            longSerde = it
            serdeMap[Long::class.java] = it
        }
    }
    fun doubleSerde(): Serde<Double, String> {
        return (doubleSerde
            ?: DoubleSerde()).also {
            doubleSerde = it
            serdeMap[Double::class.java] = it
        }
    }
    fun booleanSerde(): Serde<Boolean, String> {
        return (booleanSerde
            ?: BooleanSerde()).also {
            booleanSerde = it
            serdeMap[Boolean::class.java] = it
        }
    }
//    fun stringArraySerde(): Serde<Array<String>, String> {
//        return (stringArraySerde
//            ?: StringArraySerde()).also {
//            stringArraySerde = it
//            serdeMap[Array<String>::class.java] = it
//        }
//    }
//    fun intArraySerde(): Serde<Array<Int>, String> {
//        return (intArraySerde
//            ?: IntArraySerde()).also {
//            intArraySerde = it
//            serdeMap[IntArray::class.java] = it
//        }
//    }
//    fun longArraySerde(): Serde<Array<Long>, String> {
//        return (longArraySerde
//            ?: LongArraySerde()).also {
//            longArraySerde = it
//            serdeMap[LongArray::class.java] = it
//        }
//    }
//    fun doubleArraySerde(): Serde<Array<Double>, String> {
//        return (doubleArraySerde
//            ?: DoubleArraySerde()).also {
//            doubleArraySerde = it
//            serdeMap[DoubleArray::class.java] = it
//        }
//    }
//    fun booleanArraySerde(): Serde<Array<Boolean>, String> {
//        return (booleanArraySerde
//            ?: BooleanArraySerde()).also {
//            booleanArraySerde = it
//            serdeMap[BooleanArray::class.java] = it
//        }
//    }
//    fun jsonObjectSerde(): Serde<JSONObject, String> {
//        return (jsonObjectSerde
//            ?: JsonObjectSerde()).also {
//            jsonObjectSerde = it
//            serdeMap[JSONObject::class.java] = it
//        }
//    }
//    fun jsonArraySerde(): Serde<JSONArray, String> {
//        return (jsonArraySerde
//            ?: JsonArraySerde()).also {
//            jsonArraySerde = it
//            serdeMap[JSONArray::class.java] = it
//        }
//    }

    fun tealiumListSerde(): Serde<TealiumList, String> {
        return (tealiumListSerde
            ?: TealiumListSerde()).also {
            tealiumListSerde = it
            serdeMap[TealiumList::class.java] = it
        }
    }

    fun tealiumBundleSerde(): Serde<TealiumBundle, String> {
        return (tealiumBundleSerde
            ?: TealiumBundleSerde()).also {
            tealiumBundleSerde = it
            serdeMap[TealiumBundle::class.java] = it
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> serdeFor(clazz: Class<T>) : Serde<T, String>? {
        val mapped = serdeMap[clazz]
        if (mapped != null) {
            return mapped as Serde<T, String>
        }

        return when (clazz) {
            String::class.java -> stringSerde() as Serde<T, String>
            Int::class.java -> intSerde() as Serde<T, String>
            Double::class.java -> doubleSerde() as Serde<T, String>
            Long::class.java -> longSerde() as Serde<T, String>
            Boolean::class.java -> booleanSerde() as Serde<T, String>
//            Array<String>::class.java -> stringArraySerde() as Serde<T, String>
//            IntArray::class.java -> intArraySerde() as Serde<T, String>
//            DoubleArray::class.java -> doubleArraySerde() as Serde<T, String>
//            LongArray::class.java -> longArraySerde() as Serde<T, String>
//            BooleanArray::class.java -> booleanArraySerde() as Serde<T, String>
//            JSONObject::class.java -> jsonObjectSerde() as Serde<T, String>
//            JSONArray::class.java -> jsonArraySerde() as Serde<T, String>
            TealiumList::class.java -> tealiumListSerde() as Serde<T, String>
            TealiumBundle::class.java -> tealiumBundleSerde() as Serde<T, String>
            else -> null
        }
    }

    fun <T> serializerFor(clazz: Class<T>) : Serializer<T, String>? {
        return serdeFor(clazz)?.serializer
    }

    fun <T> deserializerFor(clazz: Class<T>) : Deserializer<T, String>? {
        return serdeFor(clazz)?.deserializer
    }
}

private open class BaseSerde<T, R>(
    override val serializer: Serializer<T, R>,
    override val deserializer: Deserializer<T, R>
) : Serde<T, R>

private class GenericSerializer<T> : Serializer<T, String> {
    override fun serialize(value: T): String {
        return value.toString()
    }
}

//private class ArraySerializer<Array> : Serializer<Array, String> {
//    override fun serialize(value: Array): String {
//        return JSONArray(value).toString()
//    }
//}

private class StringSerde : BaseSerde<String, String>(GenericSerializer(), StringDeserializer())
private class StringDeserializer : Deserializer<String, String> {
    override fun deserialize(value: String): String {
        return value
    }
}

private class IntSerde : BaseSerde<Int, String>(GenericSerializer(), IntDeserializer())
private class IntDeserializer : Deserializer<Int, String> {
    override fun deserialize(value: String): Int {
        return value.toInt()
    }
}

private class LongSerde : BaseSerde<Long, String>(GenericSerializer(), LongDeserializer())
private class LongDeserializer : Deserializer<Long, String> {
    override fun deserialize(value: String): Long {
        return value.toLong()
    }
}

private class DoubleSerde : BaseSerde<Double, String>(GenericSerializer(), DoubleDeserializer())
private class DoubleDeserializer : Deserializer<Double, String> {
    override fun deserialize(value: String): Double {
        return value.toDouble()
    }
}
private class BooleanSerde : BaseSerde<Boolean, String>(BooleanSerializer(), BooleanDeserializer())
private class BooleanSerializer : Serializer<Boolean, String> {
    override fun serialize(value: Boolean): String {
        return (if (value) 1 else 0).toString()
    }
}
private class BooleanDeserializer : Deserializer<Boolean, String> {
    override fun deserialize(value: String): Boolean {
        return value.toInt() > 0
    }
}

private class TealiumListSerde : BaseSerde<TealiumList, String>(GenericSerializer(), TealiumListDeserializer())
private class TealiumListDeserializer: Deserializer<TealiumList, String> {
    override fun deserialize(value: String): TealiumList {
        return TealiumList.fromString(value) ?: TealiumList.EMPTY_LIST
    }
}

private class TealiumBundleSerde : BaseSerde<TealiumBundle, String>(GenericSerializer(), TealiumBundleDeserializer())
private class TealiumBundleDeserializer: Deserializer<TealiumBundle, String> {
    override fun deserialize(value: String): TealiumBundle {
        return TealiumBundle.fromString(value) ?: TealiumBundle.EMPTY_BUNDLE
    }
}

/// LEGACY

//private class JsonObjectSerde : BaseSerde<JSONObject, String>(GenericSerializer(), JsonObjectDeserializer())
//private class JsonObjectDeserializer : Deserializer<JSONObject, String> {
//    override fun deserialize(value: String): JSONObject {
//        return JSONObject(value)
//    }
//}
//
//private class JsonArraySerde : BaseSerde<JSONArray, String>(GenericSerializer(), JsonArrayDeserializer())
//private class JsonArrayDeserializer : Deserializer<JSONArray, String> {
//    override fun deserialize(value: String): JSONArray {
//        return JSONArray(value)
//    }
//}
//
//private class StringArraySerde : BaseSerde<Array<String>, String>(ArraySerializer(), StringArrayDeserializer())
//private class StringArrayDeserializer : Deserializer<Array<String>, String> {
//    override fun deserialize(value: String): Array<String> {
//        val jsonArray = JSONArray(value)
//        val values = mutableListOf<String>()
//        for (i in 0 until jsonArray.length()) {
//            values.add(jsonArray[i].toString())
//        }
//        return values.toTypedArray()
//    }
//}
//
//private class IntArraySerde : BaseSerde<Array<Int>, String>(ArraySerializer(), IntArrayDeserializer())
//private class IntArrayDeserializer : Deserializer<Array<Int>, String> {
//    override fun deserialize(value: String): Array<Int> {
//        val jsonArray = JSONArray(value)
//        val values = mutableListOf<Int>()
//        for (i in 0 until jsonArray.length()) {
//            when (jsonArray[i]) {
//                is Int -> values.add(jsonArray[i] as Int)
//                is String -> values.add(jsonArray[i].toString().toInt())
//            }
//        }
//        return values.toTypedArray()
//    }
//}
//
//private class LongArraySerde : BaseSerde<Array<Long>, String>(ArraySerializer(), LongArrayDeserializer())
//private class LongArrayDeserializer : Deserializer<Array<Long>, String> {
//    override fun deserialize(value: String): Array<Long> {
//        val jsonArray = JSONArray(value)
//        val values = mutableListOf<Long>()
//        for (i in 0 until jsonArray.length()) {
//            when (jsonArray[i]) {
//                is Long -> values.add(jsonArray[i] as Long)
//                is Int -> values.add((jsonArray[i] as Int).toLong())
//                is String -> values.add(jsonArray[i].toString().toLong())
//            }
//        }
//        return values.toTypedArray()
//    }
//}
//
//private class DoubleArraySerde : BaseSerde<Array<Double>, String>(ArraySerializer(), DoubleArrayDeserializer())
//private class DoubleArrayDeserializer : Deserializer<Array<Double>, String> {
//    override fun deserialize(value: String): Array<Double> {
//        val jsonArray = JSONArray(value)
//        val values = mutableListOf<Double>()
//        for (i in 0 until jsonArray.length()) {
//            when (jsonArray[i]) {
//                is Double -> values.add(jsonArray[i] as Double)
//                is Long -> values.add((jsonArray[i] as Long).toDouble())
//                is Int -> values.add((jsonArray[i] as Int).toDouble())
//                is String -> values.add(jsonArray[i].toString().toDouble())
//            }
//        }
//        return values.toTypedArray()
//    }
//}
//
//private class BooleanArraySerde : BaseSerde<Array<Boolean>, String>(ArraySerializer(), BooleanArrayDeserializer())
//private class BooleanArrayDeserializer : Deserializer<Array<Boolean>, String> {
//    override fun deserialize(value: String): Array<Boolean> {
//        val jsonArray = JSONArray(value)
//        val values = mutableListOf<Boolean>()
//        for (i in 0 until jsonArray.length()) {
//            when (jsonArray[i]) {
//                is Boolean -> values.add(jsonArray[i] as Boolean)
//                is Int -> values.add((jsonArray[i] as Int) > 0)
//                is String -> values.add(jsonArray[i].toString().toInt() > 0)
//            }
//        }
//        return values.toTypedArray()
//    }
//}

