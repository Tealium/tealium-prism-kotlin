package com.tealium.prism.extensions.internal.lowercase

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.extensions.api.lowercase.LowercaseSettingsBuilder
import com.tealium.prism.extensions.buildTransformationSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LowercaseTransformerTests {

    private val transformer = LowercaseTransformer()
    private val scope = DispatchScope.AfterCollectors

    private fun buildSettings(
        allVariables: Boolean,
        inputs: List<ReferenceContainer> = emptyList()
    ): TransformationSettings {
        val settings = LowercaseSettingsBuilder("test_id")
        if (allVariables) {
            settings.lowercaseAllVariables()
        } else if (inputs.isNotEmpty()) {
            settings.lowercaseVariables(inputs)
        }
        return settings.setScope(TransformationScope.AfterCollectors) // required.
            .buildTransformationSettings()
    }

    @Test
    fun applyTransformation_AllVariablesPolicy_LowercasesAllStrings() {
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put("key1", "TESTVALUE")
            put("key2", "OTHERTESTVALUE")
        })
        val settings = buildSettings(allVariables = true)
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        assertEquals("testvalue", result?.payload()?.getString("key1"))
        assertEquals("othertestvalue", result?.payload()?.getString("key2"))
    }

    @Test
    fun applyTransformation_InputsPolicy_OnlyLowercasesSpecifiedInputs() {
        val ref = ReferenceContainer.key("key1")
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put("key1", "TESTVALUE")
            put("key2", "OTHERTESTVALUE")
        })
        val settings = buildSettings(allVariables = false, inputs = listOf(ref))
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        assertEquals("testvalue", result?.payload()?.getString("key1"))
        assertEquals("OTHERTESTVALUE", result?.payload()?.getString("key2"))
    }

    @Test
    fun applyTransformation_NoPolicy_NoChanges() {
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put("key1", "TESTVALUE")
        })
        val settings = buildSettings(allVariables = false, inputs = emptyList())
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        assertEquals("TESTVALUE", result?.payload()?.getString("key1"))
    }

    @Test
    fun applyTransformation_InputsPolicy_InputNotInPayload_NoChange() {
        val ref = ReferenceContainer.key("nonexistent_key")
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put("key1", "TESTVALUE")
        })
        val settings = buildSettings(allVariables = false, inputs = listOf(ref))
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        assertEquals("TESTVALUE", result?.payload()?.getString("key1"))
    }

    @Test
    fun applyTransformation_AllVariablesPolicy_LowercasesNestedObject() {
        val nestedObject = DataObject.create {
            put("nested_key", "NESTED_VALUE")
        }
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put("obj_key", nestedObject)
        })
        val settings = buildSettings(allVariables = true)
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        val nested = result?.payload()?.getDataObject("obj_key")
        assertEquals("nested_value", nested?.getString("nested_key"))
    }

    @Test
    fun applyTransformation_AllVariablesPolicy_LowercasesStringsInList() {
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put("list_key", listOf("TESTVALUE".asDataItem(), "OTHERTESTVALUE".asDataItem()).asDataList())
        })
        val settings = buildSettings(allVariables = true)
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        val list = result?.payload()?.getDataList("list_key")
        assertEquals("testvalue", list?.get(0)?.getString())
        assertEquals("othertestvalue", list?.get(1)?.getString())
    }

    @Test
    fun applyTransformation_AllVariablesPolicy_NonStringValuesUnchanged() {
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put("int_key", 42)
            put("bool_key", true)
            put("str_key", "TESTVALUE")
        })
        val settings = buildSettings(allVariables = true)
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        assertEquals(42, result?.payload()?.getInt("int_key"))
        assertEquals(true, result?.payload()?.getBoolean("bool_key"))
        assertEquals("testvalue", result?.payload()?.getString("str_key"))
    }

    @Test
    fun applyTransformation_AllVariablesPolicy_SkipsTealiumVisitorId() {
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put("key1", "TESTVALUE")
            put(Dispatch.Keys.TEALIUM_VISITOR_ID, "ABCDEF123456")
        })
        val settings = buildSettings(allVariables = true)
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        assertEquals("testvalue", result?.payload()?.getString("key1"))
        assertEquals("ABCDEF123456", result?.payload()?.getString(Dispatch.Keys.TEALIUM_VISITOR_ID))
    }

    @Test
    fun applyTransformation_InputsPolicy_LowercasesVisitorIdWhenTargeted() {
        val ref = ReferenceContainer.key(Dispatch.Keys.TEALIUM_VISITOR_ID)
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put(Dispatch.Keys.TEALIUM_VISITOR_ID, "ABCDEF123456")
        })
        val settings = buildSettings(allVariables = false, inputs = listOf(ref))
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        assertEquals("abcdef123456", result?.payload()?.getString(Dispatch.Keys.TEALIUM_VISITOR_ID))
    }

    @Test
    fun applyTransformation_AllVariablesPolicy_SkipsTealiumTraceId() {
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put("key1", "TESTVALUE")
            put(Dispatch.Keys.TEALIUM_TRACE_ID, "TRACE123ABC")
        })
        val settings = buildSettings(allVariables = true)
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        assertEquals("testvalue", result?.payload()?.getString("key1"))
        assertEquals("TRACE123ABC", result?.payload()?.getString(Dispatch.Keys.TEALIUM_TRACE_ID))
    }

    @Test
    fun applyTransformation_InputsPolicy_LowercasesTraceIdWhenTargeted() {
        val ref = ReferenceContainer.key(Dispatch.Keys.TEALIUM_TRACE_ID)
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put(Dispatch.Keys.TEALIUM_TRACE_ID, "TRACE123ABC")
        })
        val settings = buildSettings(allVariables = false, inputs = listOf(ref))
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        assertEquals("trace123abc", result?.payload()?.getString(Dispatch.Keys.TEALIUM_TRACE_ID))
    }

    @Test
    fun applyTransformation_AllVariablesPolicy_SkipsCpTraceId() {
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put("key1", "TESTVALUE")
            put(Dispatch.Keys.CP_TRACE_ID, "CPTRACE456DEF")
        })
        val settings = buildSettings(allVariables = true)
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        assertEquals("testvalue", result?.payload()?.getString("key1"))
        assertEquals("CPTRACE456DEF", result?.payload()?.getString(Dispatch.Keys.CP_TRACE_ID))
    }

    @Test
    fun applyTransformation_InputsPolicy_LowercasesCpTraceIdWhenTargeted() {
        val ref = ReferenceContainer.key(Dispatch.Keys.CP_TRACE_ID)
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put(Dispatch.Keys.CP_TRACE_ID, "CPTRACE456DEF")
        })
        val settings = buildSettings(allVariables = false, inputs = listOf(ref))
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        assertEquals("cptrace456def", result?.payload()?.getString(Dispatch.Keys.CP_TRACE_ID))
    }

    @Test
    fun applyTransformation_CompletionAlwaysCalled() {
        val dispatch = Dispatch.create("test_event")
        val settings = buildSettings(allVariables = true)
        var completionCalled = false

        transformer.applyTransformation(settings, dispatch, scope) { completionCalled = true }

        assertEquals(true, completionCalled)
    }

    @Test
    fun applyTransformation_InputsPolicy_LowercasesDataListRecursively() {
        val ref = ReferenceContainer.key("list_key")
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put("list_key", listOf("TESTVALUE".asDataItem(), "OTHERTESTVALUE".asDataItem()).asDataList())
            put("other_key", "SHOULDNOTCHANGE")
        })
        val settings = buildSettings(allVariables = false, inputs = listOf(ref))
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        val list = result?.payload()?.getDataList("list_key")
        assertEquals("testvalue", list?.get(0)?.getString())
        assertEquals("othertestvalue", list?.get(1)?.getString())
        assertEquals("SHOULDNOTCHANGE", result?.payload()?.getString("other_key"))
    }

    @Test
    fun applyTransformation_InputsPolicy_LowercasesNestedObjectRecursively() {
        val ref = ReferenceContainer.key("obj_key")
        val nestedObject = DataObject.create {
            put("nested_key", "NESTED_VALUE")
        }
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put("obj_key", nestedObject)
            put("other_key", "SHOULDNOTCHANGE")
        })
        val settings = buildSettings(allVariables = false, inputs = listOf(ref))
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        val nested = result?.payload()?.getDataObject("obj_key")
        assertEquals("nested_value", nested?.getString("nested_key"))
        assertEquals("SHOULDNOTCHANGE", result?.payload()?.getString("other_key"))
    }

    @Test
    fun applyTransformation_EmptyPayload_NoError() {
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.EMPTY_OBJECT)
        val settings = buildSettings(allVariables = true)
        var result: Dispatch? = null

        transformer.applyTransformation(settings, dispatch, scope) { result = it }

        assertEquals(dispatch, result)
    }
}
