package com.tealium.core.internal.persistence

import com.tealium.core.api.Deserializer
import com.tealium.core.api.Serde
import com.tealium.core.api.Serializer
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumList

object Serdes {
    private val serdeMap: MutableMap<Class<*>, Serde<*, String>> = mutableMapOf()

    private var stringSerde: Serde<String, String>? = null
    private var intSerde: Serde<Int, String>? = null
    private var longSerde: Serde<Long, String>? = null
    private var doubleSerde: Serde<Double, String>? = null
    private var booleanSerde: Serde<Boolean, String>? = null

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
            TealiumList::class.java -> tealiumListSerde() as Serde<T, String>
            TealiumBundle::class.java -> tealiumBundleSerde() as Serde<T, String>
            else -> null
        }
    }

    fun <T> serializerFor(clazz: Class<T>) : Serializer<T, String>? {
        return serdeFor(clazz)?.serializer
    }

    fun <T> deserializerFor(clazz: Class<T>) : Deserializer<String, T>? {
        return serdeFor(clazz)?.deserializer
    }
}

private open class BaseSerde<T, R>(
    override val serializer: Serializer<T, R>,
    override val deserializer: Deserializer<R, T>
) : Serde<T, R>

private class GenericSerializer<T> : Serializer<T, String> {
    override fun serialize(value: T): String {
        return value.toString()
    }
}

private class StringSerde : BaseSerde<String, String>(GenericSerializer(), StringDeserializer())
private class StringDeserializer : Deserializer<String, String> {
    override fun deserialize(value: String): String {
        return value
    }
}

private class IntSerde : BaseSerde<Int, String>(GenericSerializer(), IntDeserializer())
private class IntDeserializer : Deserializer<String, Int> {
    override fun deserialize(value: String): Int {
        return value.toInt()
    }
}

private class LongSerde : BaseSerde<Long, String>(GenericSerializer(), LongDeserializer())
private class LongDeserializer : Deserializer<String, Long> {
    override fun deserialize(value: String): Long {
        return value.toLong()
    }
}

private class DoubleSerde : BaseSerde<Double, String>(GenericSerializer(), DoubleDeserializer())
private class DoubleDeserializer : Deserializer<String, Double> {
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
private class BooleanDeserializer : Deserializer<String, Boolean> {
    override fun deserialize(value: String): Boolean {
        return value.toInt() > 0
    }
}

private class TealiumListSerde : BaseSerde<TealiumList, String>(GenericSerializer(), TealiumListDeserializer())
private class TealiumListDeserializer: Deserializer<String, TealiumList> {
    override fun deserialize(value: String): TealiumList {
        return TealiumList.fromString(value) ?: TealiumList.EMPTY_LIST
    }
}

private class TealiumBundleSerde : BaseSerde<TealiumBundle, String>(GenericSerializer(), TealiumBundleDeserializer())
private class TealiumBundleDeserializer: Deserializer<String, TealiumBundle> {
    override fun deserialize(value: String): TealiumBundle {
        return TealiumBundle.fromString(value) ?: TealiumBundle.EMPTY_BUNDLE
    }
}
