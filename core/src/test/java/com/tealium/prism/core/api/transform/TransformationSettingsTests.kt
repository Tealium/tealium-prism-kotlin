package com.tealium.prism.core.api.transform

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.internal.misc.Converters
import com.tealium.prism.core.internal.misc.Converters.TransformationSettingsConverter.KEY_CONDITIONS
import com.tealium.prism.core.internal.misc.Converters.TransformationSettingsConverter.KEY_CONFIGURATION
import com.tealium.prism.core.internal.misc.Converters.TransformationSettingsConverter.KEY_SCOPES
import com.tealium.prism.core.internal.misc.Converters.TransformationSettingsConverter.KEY_TRANSFORMATION_ID
import com.tealium.prism.core.internal.misc.Converters.TransformationSettingsConverter.KEY_TRANSFORMER_ID
import com.tealium.prism.core.internal.rules.conditionConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransformationSettingsTests {

    val transformationConverter = Converters.TransformationSettingsConverter

    @Test
    fun convert_Returns_Null_When_Not_DataObject() {
        assertNull(transformationConverter.convert(DataItem.NULL))
        assertNull(
            transformationConverter.convert(
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
        assertNull(transformationConverter.convert(DataList.EMPTY_LIST.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Missing_TransformerId() {
        val dataObject = createTransformationDataObject(
            transformerId = null,
            transformationId = "transformation",
            scopes = listOf(TransformationScope.AllDispatchers.value, "some_dispatcher")
        )
        assertNull(transformationConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Missing_TransformationId() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = null,
            scopes = listOf(TransformationScope.AllDispatchers.value, "some_dispatcher")
        )
        assertNull(transformationConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Returns_Null_When_Missing_Scopes() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
            scopes = null
        )
        assertNull(transformationConverter.convert(dataObject.asDataItem()))
    }

    @Test
    fun convert_Sets_Default_Configuration_To_Empty_Object_When_Omitted() {
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
            scopes = listOf("some_dispatcher")
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
            scopes = listOf("some_dispatcher")
        )
        assertNull(transformationConverter.convert(dataObject.asDataItem())!!.conditions)
    }

    @Test
    fun convert_Ignores_Scopes_That_Arent_Strings() {
        val dataObject =
            createTransformationDataObject(
                transformerId = "transformer",
                transformationId = "transformation",
                scopes = listOf(TransformationScope.AllDispatchers.value, 1, "some_dispatcher")
            )

        val transformationSettings = transformationConverter.convert(dataObject.asDataItem())!!
        assertEquals(2, transformationSettings.scope.size)
        assertEquals(TransformationScope.AllDispatchers, transformationSettings.scope.elementAt(0))
        assertEquals(
            TransformationScope.Dispatcher("some_dispatcher"),
            transformationSettings.scope.elementAt(1)
        )
    }

    @Test
    fun convert_Creates_New_TransformationSettings() {
        val conditions = Rule.just(Condition.isDefined(null, "key"))
        val dataObject = createTransformationDataObject(
            transformerId = "transformer",
            transformationId = "transformation",
            scopes = listOf(
                TransformationScope.AllDispatchers,
                TransformationScope.AfterCollectors,
                "some_dispatcher"
            ),
            configuration = DataObject.create { put("key", "value") },
            conditions = conditions
        )

        val transformationSettings = transformationConverter.convert(dataObject.asDataItem())!!
        assertEquals("transformation", transformationSettings.id)
        assertEquals("transformer", transformationSettings.transformerId)
        assertEquals(3, transformationSettings.scope.size)
        assertEquals(TransformationScope.AllDispatchers, transformationSettings.scope.elementAt(0))
        assertEquals(TransformationScope.AfterCollectors, transformationSettings.scope.elementAt(1))
        assertEquals(
            TransformationScope.Dispatcher("some_dispatcher"),
            transformationSettings.scope.elementAt(2)
        )
        assertEquals("value", transformationSettings.configuration.getString("key"))
        assertEquals(conditions, transformationSettings.conditions)
    }

    @Test
    fun asDataItem_Returns_All_Fields_As_DataObject() {
        val conditions = Rule.just(Condition.isDefined(null, "key"))
        val transformationSettings =
            TransformationSettings(
                "transformation",
                "transformer",
                setOf(
                    TransformationScope.AllDispatchers,
                    TransformationScope.AfterCollectors,
                    TransformationScope.Dispatcher("dispatcher1")
                ),
                DataObject.create { put("key", "value") },
                conditions
            )

        val dataItem = transformationSettings.asDataItem()
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
        assertEquals(
            "value",
            dataObject.getDataObject(KEY_CONFIGURATION)!!.getString("key")
        )
        assertEquals(
            conditions,
            dataObject.get(KEY_CONDITIONS, conditionConverter)
        )
    }

    @Test
    fun dataItemConvertible_Converted_Returns_Equal_Object() {
        val transformationSettings =
            TransformationSettings(
                "transformation",
                "transformer",
                setOf(
                    TransformationScope.AllDispatchers,
                    TransformationScope.AfterCollectors,
                    TransformationScope.Dispatcher("dispatcher1")
                ),
                DataObject.create { put("key", "value") }
            )

        val converted = transformationConverter.convert(transformationSettings.asDataItem())

        assertEquals(transformationSettings, converted)
    }


    private fun createTransformationDataObject(
        transformerId: String?,
        transformationId: String?,
        scopes: List<Any>?,
        configuration: DataObject? = null,
        conditions: Rule<Condition>? = null
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
            configuration?.let {
                put(KEY_CONFIGURATION, it)
            }
            conditions?.let {
                put(KEY_CONDITIONS, it)
            }
        }
    }
}