package com.tealium.prism.core.api.transform

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.transform.TransformationSettings.Converter.KEY_CONDITIONS
import com.tealium.prism.core.api.transform.TransformationSettings.Converter.KEY_CONFIGURATION
import com.tealium.prism.core.api.transform.TransformationSettings.Converter.KEY_ORDER
import com.tealium.prism.core.api.transform.TransformationSettings.Converter.KEY_SCOPE
import com.tealium.prism.core.api.transform.TransformationSettings.Converter.KEY_TRANSFORMATION_ID
import com.tealium.prism.core.api.transform.TransformationSettings.Converter.KEY_TRANSFORMER_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransformationSettingsTests {

    val transformationConverter = TransformationSettings.Converter

    @Test
    fun convert_Returns_Null_When_Not_DataObject() {
        assertNull(transformationConverter.convert(DataItem.NULL))
        assertNull(
            transformationConverter.convert(
                DataItem.string(
                    """{
                "transformer_id": "test",
                "transformation_id": "test",
                "scope": "${TransformationScope.AllDispatchers.STRING_VALUE}"
            }""".trimMargin()
                )
            )
        )
        assertNull(transformationConverter.convert(DataList.EMPTY_LIST.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Missing_TransformerId() {
        val dataObject = createTransformationDataObject(
            transformerId = null,
            transformationId = "transformation",
        )
        assertNull(transformationConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Missing_TransformationId() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = null,
        )
        assertNull(transformationConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Missing_Scope() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
            scope = null
        )
        assertNull(transformationConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Invalid_Scope_Name() {
        val dataObject =
            createTransformationDataObject(
                transformerId = "transformer",
                transformationId = "transformation",
                scope = "unsupported".asDataItem() // can only be "alldispatchers" or "aftercollectors"
            )

        val transformationSettings = transformationConverter.convert(dataObject.asDataItem())
        assertNull(transformationSettings)
    }

    @Test
    fun convert_Returns_Null_When_Invalid_Scope_Type() {
        val dataObject =
            createTransformationDataObject(
                transformerId = "transformer",
                transformationId = "transformation",
                scope = 1.asDataItem() // can only be string or list
            )

        val transformationSettings = transformationConverter.convert(dataObject.asDataItem())
        assertNull(transformationSettings)
    }

    @Test
    fun convert_Sets_Default_Configuration_To_Empty_Object_When_Omitted() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
        )
        assertEquals(
            DataObject.EMPTY_OBJECT,
            transformationConverter.convert(dataObject.asDataItem())!!.configuration
        )
    }

    @Test
    fun convert_Sets_Conditions_To_Null_When_Omitted() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
        )
        assertNull(transformationConverter.convert(dataObject.asDataItem())!!.conditions)
    }

    @Test
    fun convert_Creates_New_TransformationSettings() {
        val conditions = Rule.just(Condition.isDefined("key"))
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
            scope = TransformationScope.AllDispatchers,
            configuration = DataObject.create { put("key", "value") },
            conditions = conditions
        )

        val transformationSettings = transformationConverter.convert(dataObject.asDataItem())!!
        assertEquals("transformation", transformationSettings.id)
        assertEquals("transformer", transformationSettings.transformerId)
        assertEquals(TransformationScope.AllDispatchers, transformationSettings.scope)
        assertEquals("value", transformationSettings.configuration.getString("key"))
        assertEquals(conditions, transformationSettings.conditions)
    }

    @Test
    fun convert_Converts_AllDispatchers_Scope() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
            scope = TransformationScope.AllDispatchers,
        )

        val transformationSettings = transformationConverter.convert(dataObject.asDataItem())!!
        assertEquals(TransformationScope.AllDispatchers, transformationSettings.scope)
    }

    @Test
    fun convert_Converts_AfterCollectors_Scope() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
            scope = TransformationScope.AfterCollectors,
        )

        val transformationSettings = transformationConverter.convert(dataObject.asDataItem())!!
        assertEquals(TransformationScope.AfterCollectors, transformationSettings.scope)
    }

    @Test
    fun convert_Converts_Dispatchers_Scope() {
        val scope = TransformationScope.Dispatchers("d1", "d2")
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
            scope = scope,
        )

        val transformationSettings = transformationConverter.convert(dataObject.asDataItem())!!
        assertEquals(scope, transformationSettings.scope)
    }

    @Test
    fun convert_Sets_Default_Order_To_MaxValue_When_Omitted() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
        )

        val transformationSettings = transformationConverter.convert(dataObject.asDataItem())!!
        assertEquals(Int.MAX_VALUE, transformationSettings.order)
    }

    @Test
    fun convert_Sets_Order_When_Provided() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
            order = 5,
        )

        val transformationSettings = transformationConverter.convert(dataObject.asDataItem())!!
        assertEquals(5, transformationSettings.order)
    }

    private fun createTransformationDataObject(
        transformerId: String?,
        transformationId: String?,
        scope: DataItemConvertible? = TransformationScope.AllDispatchers,
        configuration: DataObject? = null,
        conditions: Rule<Condition>? = null,
        order: Int? = null
    ): DataObject {
        return DataObject.create {
            transformerId?.let {
                put(KEY_TRANSFORMER_ID, it)
            }
            transformationId?.let {
                put(KEY_TRANSFORMATION_ID, it)
            }
            scope?.let {
                put(KEY_SCOPE, scope)
            }
            configuration?.let {
                put(KEY_CONFIGURATION, it)
            }
            conditions?.let {
                put(KEY_CONDITIONS, it)
            }
            order?.let {
                put(KEY_ORDER, it)
            }
        }
    }
}