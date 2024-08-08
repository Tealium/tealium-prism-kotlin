package com.tealium.core.api.barriers

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumList
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.internal.misc.Deserializers.ScopedBarrierDeserializable.KEY_BARRIER_ID
import com.tealium.core.internal.misc.Deserializers.ScopedBarrierDeserializable.KEY_SCOPES
import com.tealium.core.internal.misc.Deserializers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScopedBarrierTests {

    val deserializer = Deserializers.ScopedBarrierDeserializable

    @Test
    fun deserialize_Returns_Null_When_Not_Bundle() {
        assertNull(deserializer.deserialize(TealiumValue.NULL))
        assertNull(
            deserializer.deserialize(
                TealiumValue.string(
                    """{
                "barrier_id": "test",
                "scopes": [
                    "${BarrierScope.All.value}", "some_dispatcher"
                ]
            }""".trimMargin()
                )
            )
        )
        assertNull(deserializer.deserialize(TealiumList.EMPTY_LIST.asTealiumValue()))
    }

    @Test
    fun deserialize_Returns_Null_When_Missing_BarriedId() {
        val bundle = createBarrierBundle(
            barrierId = null,
            scopes = listOf(BarrierScope.All.value, "some_dispatcher")
        )
        assertNull(deserializer.deserialize(bundle.asTealiumValue()))
    }

    @Test
    fun deserialize_Returns_Null_When_Missing_Scopes() {
        val bundle = createBarrierBundle(barrierId = "test", scopes = null)
        assertNull(deserializer.deserialize(bundle.asTealiumValue()))
    }

    @Test
    fun deserialize_Ignores_Scopes_That_Arent_Strings() {
        val bundle = createBarrierBundle(
            barrierId = "test",
            scopes = listOf(BarrierScope.All.value, 1, "some_dispatcher")
        )

        val scopedBarrier = deserializer.deserialize(bundle.asTealiumValue())!!
        assertEquals(2, scopedBarrier.scope.size)
        assertEquals(BarrierScope.All, scopedBarrier.scope.elementAt(0))
        assertEquals(BarrierScope.Dispatcher("some_dispatcher"), scopedBarrier.scope.elementAt(1))
    }

    @Test
    fun deserialize_Creates_New_Scoped_Barrier() {
        val bundle = createBarrierBundle(
            barrierId = "test",
            scopes = listOf(BarrierScope.All.value, "some_dispatcher", "other_dispatcher")
        )

        val scopedBarrier = deserializer.deserialize(bundle.asTealiumValue())!!
        assertEquals("test", scopedBarrier.barrierId)
        assertEquals(3, scopedBarrier.scope.size)
        assertEquals(BarrierScope.All, scopedBarrier.scope.elementAt(0))
        assertEquals(BarrierScope.Dispatcher("some_dispatcher"), scopedBarrier.scope.elementAt(1))
        assertEquals(BarrierScope.Dispatcher("other_dispatcher"), scopedBarrier.scope.elementAt(2))
    }

    @Test
    fun serializable_Returns_All_Fields_As_Bundle() {
        val scopedBarrier =
            ScopedBarrier("testId", setOf(BarrierScope.All, BarrierScope.Dispatcher("dispatcher1")))

        val serialized = scopedBarrier.asTealiumValue()
        val bundle = serialized.getBundle()!!

        assertEquals("testId", bundle.getString(KEY_BARRIER_ID))
        assertEquals(
            BarrierScope.All.value,
            bundle.getList(KEY_SCOPES)!!.getString(0)
        )
        assertEquals(
            BarrierScope.Dispatcher("dispatcher1").value,
            bundle.getList(KEY_SCOPES)!!.getString(1)
        )
    }

    @Test
    fun serializable_Deserialized_Returns_Equal_Object() {
        val scopedBarrier =
            ScopedBarrier("testId", setOf(BarrierScope.All, BarrierScope.Dispatcher("dispatcher1")))

        val deserialized = deserializer.deserialize(scopedBarrier.asTealiumValue())

        assertEquals(scopedBarrier, deserialized)
    }


    private fun createBarrierBundle(barrierId: String?, scopes: List<Any>?): TealiumBundle {
        return TealiumBundle.create {
            barrierId?.let {
                put(KEY_BARRIER_ID, it)
            }
            scopes?.let {
                put(KEY_SCOPES, TealiumValue.convert(scopes))
            }
        }
    }
}