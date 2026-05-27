package com.tealium.prism.jstransformer.internal

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.jstransformer.JavaScriptEngineAdapter
import com.tealium.prism.jstransformer.JavaScriptTransformationSettingsBuilder
import com.tealium.prism.jstransformer.JavaScriptTransformerFactory
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JavaScriptTransformerTests {

    @RelaxedMockK
    private lateinit var mockAdapter: JavaScriptEngineAdapter

    @RelaxedMockK
    private lateinit var mockLogger: Logger

    @RelaxedMockK
    private lateinit var mockDispatch: Dispatch

    private lateinit var transformer: JavaScriptTransformer

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { mockDispatch.payload() } returns DataObject.EMPTY_OBJECT
        transformer = JavaScriptTransformer(mockAdapter, mockLogger)
    }

    private fun settingsWithCode(jsCode: String): TransformationSettings {
        return JavaScriptTransformationSettingsBuilder(
            transformationId = "test_transformation"
        ).setJsCode(jsCode).build()
    }

    private fun settingsWithNoCode(): TransformationSettings {
        return JavaScriptTransformationSettingsBuilder(
            transformationId = "test_transformation"
        ).build()
    }

    @Test
    fun applyTransformation_CallsCompletionWithDispatch_WhenJsCodeIsNull() {
        val completion = mockk<(Dispatch?) -> Unit>(relaxed = true)

        transformer.applyTransformation(
            settingsWithNoCode(),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify { completion.invoke(mockDispatch) }
        verify(inverse = true) { mockAdapter.evaluateJavaScript(any()) }
    }

    @Test
    fun applyTransformation_CallsCompletionWithDispatch_WhenJsCodeIsBlank() {
        val completion = mockk<(Dispatch?) -> Unit>(relaxed = true)

        transformer.applyTransformation(
            settingsWithCode("   "),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify { completion.invoke(mockDispatch) }
        verify(inverse = true) { mockAdapter.evaluateJavaScript(any()) }
    }

    @Test
    fun applyTransformation_CallsCompletionWithDispatch_WhenJsCodeIsEmpty() {
        val completion = mockk<(Dispatch?) -> Unit>(relaxed = true)

        transformer.applyTransformation(
            settingsWithCode(""),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify { completion.invoke(mockDispatch) }
        verify(inverse = true) { mockAdapter.evaluateJavaScript(any()) }
    }

    @Test
    fun applyTransformation_ReplacesDispatchPayload_WhenAdapterReturnsDataObject() {
        val originalPayload = DataObject.create { put("original_key", "original_value") }
        every { mockDispatch.payload() } returns originalPayload

        val newPayload = DataObject.create { put("modified_key", "modified_value") }
        every { mockAdapter.evaluateJavaScript(any()) } returns Observables.just(
            TealiumResult.success(newPayload.asDataItem())
        )

        val completion = mockk<(Dispatch?) -> Unit>(relaxed = true)
        transformer.applyTransformation(
            settingsWithCode("payload.modified_key = 'modified_value';"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify { mockDispatch.replace(newPayload) }
        verify { completion.invoke(mockDispatch) }
    }

    @Test
    fun applyTransformation_CallsCompletionWithNull_WhenAdapterReturnsNullDataItem() {
        every { mockAdapter.evaluateJavaScript(any()) } returns Observables.just(
            TealiumResult.success(DataItem.NULL)
        )

        val completion = mockk<(Dispatch?) -> Unit>(relaxed = true)
        transformer.applyTransformation(
            settingsWithCode("drop()"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify { completion.invoke(null) }
        verify(inverse = true) { mockDispatch.replace(any()) }
    }

    @Test
    fun applyTransformation_CallsCompletionWithDispatch_WhenAdapterResultThrows() {
        every { mockAdapter.evaluateJavaScript(any()) } returns Observables.just(
            TealiumResult.failure(RuntimeException("JS evaluation error"))
        )

        val completion = mockk<(Dispatch?) -> Unit>(relaxed = true)
        transformer.applyTransformation(
            settingsWithCode("throw new Error('boom')"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify { completion.invoke(mockDispatch) }
    }

    @Test
    fun applyTransformation_DoesNotReplacePayload_WhenAdapterResultThrows() {
        every { mockAdapter.evaluateJavaScript(any()) } returns Observables.just(
            TealiumResult.failure(RuntimeException("JS evaluation error"))
        )

        val completion = mockk<(Dispatch?) -> Unit>(relaxed = true)
        transformer.applyTransformation(
            settingsWithCode("throw new Error('boom')"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify(inverse = true) { mockDispatch.replace(any()) }
    }

    @Test
    fun applyTransformation_DoesNotInjectNormalTrackFunction_WhenDispatchHasJsTrackingTrue() {
        every { mockDispatch.payload() } returns DataObject.create { put("js_tracking", true) }

        val jsSlot = slot<String>()
        every { mockAdapter.evaluateJavaScript(capture(jsSlot)) } returns Observables.just(
            TealiumResult.success(DataObject.EMPTY_OBJECT.asDataItem())
        )

        transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            mockk(relaxed = true)
        )

        assertFalse(
            "Expected no tracker.track.apply when js_tracking is true",
            jsSlot.captured.contains("tracker.track.apply")
        )
    }

    @Test
    fun applyTransformation_InjectsNormalTrackFunction_WhenDispatchDoesNotHaveJsTracking() {
        every { mockDispatch.payload() } returns DataObject.create { put("tealium_event", "test") }

        val jsSlot = slot<String>()
        every { mockAdapter.evaluateJavaScript(capture(jsSlot)) } returns Observables.just(
            TealiumResult.success(DataObject.EMPTY_OBJECT.asDataItem())
        )

        transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            mockk(relaxed = true)
        )

        assertTrue(
            "Expected tracker.track.apply in track function",
            jsSlot.captured.contains("tracker.track.apply")
        )
    }

    @Test
    fun applyTransformation_InjectsNormalTrackFunction_WhenJsTrackingIsFalse() {
        val jsSlot = slot<String>()
        every { mockAdapter.evaluateJavaScript(capture(jsSlot)) } returns Observables.just(
            TealiumResult.success(DataObject.EMPTY_OBJECT.asDataItem())
        )

        transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            mockk(relaxed = true)
        )

        assertTrue(
            "Expected tracker.track.apply in track function when js_tracking is false",
            jsSlot.captured.contains("tracker.track.apply")
        )
    }

    @Test
    fun applyTransformation_IncludesDispatchScopeInJs() {
        val jsSlot = slot<String>()
        every { mockAdapter.evaluateJavaScript(capture(jsSlot)) } returns Observables.just(
            TealiumResult.success(DataObject.EMPTY_OBJECT.asDataItem())
        )

        transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            mockk(relaxed = true)
        )

        assertTrue(
            "Expected scope string in generated JS",
            jsSlot.captured.contains(DispatchScope.AfterCollectors.toString())
        )
    }

    @Test
    fun applyTransformation_IncludesUserJsCode_InEvaluatedScript() {
        val userCode = "payload.custom_key = 'hello';"
        val jsSlot = slot<String>()
        every { mockAdapter.evaluateJavaScript(capture(jsSlot)) } returns Observables.just(
            TealiumResult.success(DataObject.EMPTY_OBJECT.asDataItem())
        )

        transformer.applyTransformation(
            settingsWithCode(userCode),
            mockDispatch,
            DispatchScope.AfterCollectors,
            mockk(relaxed = true)
        )

        assertTrue("Expected user JS code in evaluated script", jsSlot.captured.contains(userCode))
    }

    @Test
    fun applyTransformation_EscapesScopeString_WhenScopeContainsSpecialCharacters() {
        val jsSlot = slot<String>()
        every { mockAdapter.evaluateJavaScript(capture(jsSlot)) } returns Observables.just(
            TealiumResult.success(DataObject.EMPTY_OBJECT.asDataItem())
        )

        val specialScope = DispatchScope.Dispatcher("dispatcher\"with\nnewline")
        transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            specialScope,
            mockk(relaxed = true)
        )

        assertTrue(jsSlot.captured.contains("dispatcher\\\"with\\nnewline"))
    }

    @Test
    fun onShutdown_DisposesAdapter() {
        transformer.onShutdown()

        verify { mockAdapter.dispose() }
    }

    @Test
    fun id_ReturnsJavaScriptTransformerModuleType() {
        assertTrue(transformer.id == JavaScriptTransformerFactory.MODULE_TYPE)
    }
}
