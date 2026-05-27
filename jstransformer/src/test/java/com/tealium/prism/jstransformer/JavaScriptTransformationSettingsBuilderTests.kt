package com.tealium.prism.jstransformer

import com.tealium.prism.jstransformer.internal.JavaScriptTransformer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JavaScriptTransformationSettingsBuilderTests {

    @Test
    fun build_SetsTransformationId_FromConstructorArgument() {
        val result = JavaScriptTransformationSettingsBuilder("my_transformation").build()

        assertEquals("my_transformation", result.id)
    }

    @Test
    fun build_SetsTransformerId_ToJsTransformerModuleType() {
        val result = JavaScriptTransformationSettingsBuilder("test").build()

        assertEquals(JavaScriptTransformerFactory.MODULE_TYPE, result.transformerId)
    }

    @Test
    fun build_Configuration_HasNoJsCode_WhenSetJsCodeNotCalled() {
        val result = JavaScriptTransformationSettingsBuilder("test").build()

        assertNull(result.configuration.getString(JavaScriptTransformer.Keys.JS_CODE))
    }

    @Test
    fun setJsCode_SetsJsCode_InConfiguration() {
        val jsCode = "payload.custom = 'value';"
        val result = JavaScriptTransformationSettingsBuilder("test")
            .setJsCode(jsCode)
            .build()

        assertEquals(jsCode, result.configuration.getString(JavaScriptTransformer.Keys.JS_CODE))
    }

    @Test
    fun setJsCode_OverwritesPreviousValue_WhenCalledTwice() {
        val result = JavaScriptTransformationSettingsBuilder("test")
            .setJsCode("first_code();")
            .setJsCode("second_code();")
            .build()

        assertEquals("second_code();", result.configuration.getString(JavaScriptTransformer.Keys.JS_CODE))
    }

    @Test
    fun setJsCode_PreservesJsCode_WithMultilineString() {
        val jsCode = """
            var x = payload.value;
            if (x > 10) {
                payload.result = 'high';
            }
        """.trimIndent()

        val result = JavaScriptTransformationSettingsBuilder("test")
            .setJsCode(jsCode)
            .build()

        assertEquals(jsCode, result.configuration.getString(JavaScriptTransformer.Keys.JS_CODE))
    }
}
