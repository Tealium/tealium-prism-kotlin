package com.tealium.core.api.transform

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumList
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.internal.misc.Deserializers
import com.tealium.core.internal.misc.Deserializers.ScopedTransformationDeserializable.KEY_SCOPES
import com.tealium.core.internal.misc.Deserializers.ScopedTransformationDeserializable.KEY_TRANSFORMATION_ID
import com.tealium.core.internal.misc.Deserializers.ScopedTransformationDeserializable.KEY_TRANSFORMER_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScopedTransformationTests {

    val deserializer = Deserializers.ScopedTransformationDeserializable

    @Test
    fun deserialize_Returns_Null_When_Not_Bundle() {
        assertNull(deserializer.deserialize(TealiumValue.NULL))
        assertNull(
            deserializer.deserialize(
                TealiumValue.string(
                    """{
                "transformer_id": "test",
                "transformation_id": "test",
                "scopes": [
                    "${TransformationScope.AllDispatchers.value}", "some_dispatcher"
                ]
            }""".trimMargin()
                )
            )
        )
        assertNull(deserializer.deserialize(TealiumList.EMPTY_LIST.asTealiumValue()))
    }

    @Test
    fun deserialize_Returns_Null_When_Missing_TransformerId() {
        val bundle = createTransformationBundle(
            transformerId = null,
            transformationId = "transformation",
            scopes = listOf(TransformationScope.AllDispatchers.value, "some_dispatcher")
        )
        assertNull(deserializer.deserialize(bundle.asTealiumValue()))
    }

    @Test
    fun deserialize_Returns_Null_When_Missing_TransformationId() {
        val bundle = createTransformationBundle(
            transformerId = "transformer",
            transformationId = null,
            scopes = listOf(TransformationScope.AllDispatchers.value, "some_dispatcher")
        )
        assertNull(deserializer.deserialize(bundle.asTealiumValue()))
    }

    @Test
    fun deserialize_Returns_Null_When_Missing_Scopes() {
        val bundle = createTransformationBundle(
            transformerId = "transformer",
            transformationId = "transformation",
            scopes = null
        )
        assertNull(deserializer.deserialize(bundle.asTealiumValue()))
    }

    @Test
    fun deserialize_Ignores_Scopes_That_Arent_Strings() {
        val bundle =
            createTransformationBundle(
                transformerId = "transformer",
                transformationId = "transformation",
                scopes = listOf(TransformationScope.AllDispatchers.value, 1, "some_dispatcher")
            )

        val scopedTransformation = deserializer.deserialize(bundle.asTealiumValue())!!
        assertEquals(2, scopedTransformation.scope.size)
        assertEquals(TransformationScope.AllDispatchers, scopedTransformation.scope.elementAt(0))
        assertEquals(
            TransformationScope.Dispatcher("some_dispatcher"),
            scopedTransformation.scope.elementAt(1)
        )
    }

    @Test
    fun deserialize_Creates_New_Scoped_Barrier() {
        val bundle = createTransformationBundle(
            transformerId = "transformer",
            transformationId = "transformation",
            scopes = listOf(
                TransformationScope.AllDispatchers,
                TransformationScope.AfterCollectors,
                "some_dispatcher"
            )
        )

        val scopedTransformation = deserializer.deserialize(bundle.asTealiumValue())!!
        assertEquals("transformation", scopedTransformation.id)
        assertEquals("transformer", scopedTransformation.transformerId)
        assertEquals(3, scopedTransformation.scope.size)
        assertEquals(TransformationScope.AllDispatchers, scopedTransformation.scope.elementAt(0))
        assertEquals(TransformationScope.AfterCollectors, scopedTransformation.scope.elementAt(1))
        assertEquals(
            TransformationScope.Dispatcher("some_dispatcher"),
            scopedTransformation.scope.elementAt(2)
        )
    }

    @Test
    fun serializable_Returns_All_Fields_As_Bundle() {
        val scopedTransformation =
            ScopedTransformation(
                "transformation",
                "transformer",
                setOf(
                    TransformationScope.AllDispatchers,
                    TransformationScope.AfterCollectors,
                    TransformationScope.Dispatcher("dispatcher1")
                )
            )

        val serialized = scopedTransformation.asTealiumValue()
        val bundle = serialized.getBundle()!!

        assertEquals("transformation", bundle.getString(KEY_TRANSFORMATION_ID))
        assertEquals("transformer", bundle.getString(KEY_TRANSFORMER_ID))
        assertEquals(
            TransformationScope.AllDispatchers.value,
            bundle.getList(KEY_SCOPES)!!.getString(0)
        )
        assertEquals(
            TransformationScope.AfterCollectors.value,
            bundle.getList(KEY_SCOPES)!!.getString(1)
        )
        assertEquals(
            TransformationScope.Dispatcher("dispatcher1").value,
            bundle.getList(KEY_SCOPES)!!.getString(2)
        )
    }

    @Test
    fun serializable_Deserialized_Returns_Equal_Object() {
        val scopedTransformation =
            ScopedTransformation(
                "transformation",
                "transformer",
                setOf(
                    TransformationScope.AllDispatchers,
                    TransformationScope.AfterCollectors,
                    TransformationScope.Dispatcher("dispatcher1")
                )
            )

        val deserialized = deserializer.deserialize(scopedTransformation.asTealiumValue())

        assertEquals(scopedTransformation, deserialized)
    }


    private fun createTransformationBundle(
        transformerId: String?,
        transformationId: String?,
        scopes: List<Any>?
    ): TealiumBundle {
        return TealiumBundle.create {
            transformerId?.let {
                put(KEY_TRANSFORMER_ID, it)
            }
            transformationId?.let {
                put(KEY_TRANSFORMATION_ID, it)
            }
            scopes?.let {
                put(KEY_SCOPES, TealiumValue.convert(scopes))
            }
        }
    }
}