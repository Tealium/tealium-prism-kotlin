package com.tealium.prism.core.api.transform

import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.rules.Condition.Companion.isDefined
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.transform.TransformationSettings.Converter
import com.tealium.prism.core.internal.rules.conditionConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransformationSettingsBuilderTests {

    @Test
    fun build_Sets_TransformationId_In_DataObject() {
        val settings = TestTransformationSettingsBuilder("test")
            .build()

        assertEquals("test", settings.getString(Converter.KEY_TRANSFORMATION_ID))
    }

    @Test
    fun build_Sets_TransformerId_In_DataObject() {
        val settings = TestTransformationSettingsBuilder("test", "transformer")
            .build()

        assertEquals("transformer", settings.getString(Converter.KEY_TRANSFORMER_ID))
    }

    @Test
    fun build_Sets_Configuration_In_DataObject_When_Configured() {
        val config = DataObject.create { put("key", "value") }
        val settings = TestTransformationSettingsBuilder("test", config = config)
            .build()

        assertEquals(config, settings.getDataObject(Converter.KEY_CONFIGURATION))
    }

    @Test
    fun build_Sets_Order_In_DataObject_When_Configured() {
        val settings = TestTransformationSettingsBuilder("test")
            .setOrder(5)
            .build()

        assertEquals(5, settings.getInt(Converter.KEY_ORDER))
    }

    @Test
    fun build_Does_Not_Set_Order_In_DataObject_When_Not_Configured() {
        val settings = TestTransformationSettingsBuilder("test")
            .build()

        assertNull(settings.get(Converter.KEY_ORDER))
    }

    @Test
    fun build_Sets_Conditions_In_DataObject_When_Configured() {
        val condition = Rule.just(isDefined("something"))
        val settings = TestTransformationSettingsBuilder("test")
            .setCondition(condition)
            .build()

        assertEquals(condition, settings.get(Converter.KEY_CONDITIONS, conditionConverter))
    }

    @Test
    fun build_Does_Not_Set_Conditions_In_DataObject_When_Not_Configured() {
        val settings = TestTransformationSettingsBuilder("test")
            .build()

        assertNull(settings.get(Converter.KEY_CONDITIONS))
    }

    @Test
    fun build_Sets_String_Scope_In_DataObject_When_AfterCollectors_Configured() {
        val scope = TransformationScope.AfterCollectors
        val settings = TestTransformationSettingsBuilder("test")
            .setScope(scope)
            .build()

        assertEquals(scope.STRING_VALUE, settings.getString(Converter.KEY_SCOPE))
    }

    @Test
    fun build_Sets_String_Scope_In_DataObject_When_AllDispatchers_Configured() {
        val scope = TransformationScope.AllDispatchers
        val settings = TestTransformationSettingsBuilder("test")
            .setScope(scope)
            .build()

        assertEquals(scope.STRING_VALUE, settings.getString(Converter.KEY_SCOPE))
    }

    @Test
    fun build_Sets_DataList_Scope_In_DataObject_When_Specific_Dispatchers_Configured() {
        val scope = TransformationScope.Dispatchers(listOf("dispatcher1", "dispatcher2"))
        val settings = TestTransformationSettingsBuilder("test")
            .setScope(scope)
            .build()

        val expected = DataList.create { add("dispatcher1"); add("dispatcher2") }
        assertEquals(expected, settings.getDataList(Converter.KEY_SCOPE))
    }

    @Test
    fun build_Does_Not_Set_Scope_In_DataObject_When_Not_Configured() {
        val settings = TestTransformationSettingsBuilder("test")
            .build()

        assertNull(settings.get(Converter.KEY_SCOPE))
    }
}