package com.tealium.core.api.transform

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataItem
import com.tealium.core.internal.misc.Converters
import com.tealium.core.internal.misc.Converters.ScopedTransformationConverter.KEY_SCOPES
import com.tealium.core.internal.misc.Converters.ScopedTransformationConverter.KEY_TRANSFORMATION_ID
import com.tealium.core.internal.misc.Converters.ScopedTransformationConverter.KEY_TRANSFORMER_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScopedTransformationTests {

    val scopedTransformationConverter = Converters.ScopedTransformationConverter

    @Test
    fun convert_Returns_Null_When_Not_DataObject() {
        assertNull(scopedTransformationConverter.convert(DataItem.NULL))
        assertNull(
            scopedTransformationConverter.convert(
                DataItem.string(
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
        assertNull(scopedTransformationConverter.convert(DataList.EMPTY_LIST.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Missing_TransformerId() {
        val dataObject = createTransformationDataObject(
            transformerId = null,
            transformationId = "transformation",
            scopes = listOf(TransformationScope.AllDispatchers.value, "some_dispatcher")
        )
        assertNull(scopedTransformationConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Missing_TransformationId() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = null,
            scopes = listOf(TransformationScope.AllDispatchers.value, "some_dispatcher")
        )
        assertNull(scopedTransformationConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Missing_Scopes() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
            scopes = null
        )
        assertNull(scopedTransformationConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Ignores_Scopes_That_Arent_Strings() {
        val dataObject =
            createTransformationDataObject(
                transformerId = "transformer",
                transformationId = "transformation",
                scopes = listOf(TransformationScope.AllDispatchers.value, 1, "some_dispatcher")
            )

        val scopedTransformation = scopedTransformationConverter.convert(dataObject.asDataItem())!!
        assertEquals(2, scopedTransformation.scope.size)
        assertEquals(TransformationScope.AllDispatchers, scopedTransformation.scope.elementAt(0))
        assertEquals(
            TransformationScope.Dispatcher("some_dispatcher"),
            scopedTransformation.scope.elementAt(1)
        )
    }

    @Test
    fun convert_Creates_New_Scoped_Barrier() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
            scopes = listOf(
                TransformationScope.AllDispatchers,
                TransformationScope.AfterCollectors,
                "some_dispatcher"
            )
        )

        val scopedTransformation = scopedTransformationConverter.convert(dataObject.asDataItem())!!
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
    fun asDataItem_Returns_All_Fields_As_DataObject() {
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

        val dataItem = scopedTransformation.asDataItem()
        val dataObject = dataItem.getDataObject()!!

        assertEquals("transformation", dataObject.getString(KEY_TRANSFORMATION_ID))
        assertEquals("transformer", dataObject.getString(KEY_TRANSFORMER_ID))
        assertEquals(
            TransformationScope.AllDispatchers.value,
            dataObject.getDataList(KEY_SCOPES)!!.getString(0)
        )
        assertEquals(
            TransformationScope.AfterCollectors.value,
            dataObject.getDataList(KEY_SCOPES)!!.getString(1)
        )
        assertEquals(
            TransformationScope.Dispatcher("dispatcher1").value,
            dataObject.getDataList(KEY_SCOPES)!!.getString(2)
        )
    }

    @Test
    fun dataItemConvertible_Converted_Returns_Equal_Object() {
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

        val converted = scopedTransformationConverter.convert(scopedTransformation.asDataItem())

        assertEquals(scopedTransformation, converted)
    }


    private fun createTransformationDataObject(
        transformerId: String?,
        transformationId: String?,
        scopes: List<Any>?
    ): DataObject {
        return DataObject.create {
            transformerId?.let {
                put(KEY_TRANSFORMER_ID, it)
            }
            transformationId?.let {
                put(KEY_TRANSFORMATION_ID, it)
            }
            scopes?.let {
                put(KEY_SCOPES, DataItem.convert(scopes))
            }
        }
    }
}