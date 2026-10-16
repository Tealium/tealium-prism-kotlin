package com.tealium.prism.jstransformer.internal

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.jstransformer.JavaScriptEngineAdapter
import com.tealium.prism.jstransformer.JavaScriptTransformationSettingsBuilder
import com.tealium.prism.jstransformer.JavaScriptTransformerFactory
import com.tealium.tests.common.ManualScheduler
import com.tealium.tests.common.buildTransformationSettings
import io.mockk.CapturingSlot
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

    private lateinit var transformer: JavaScriptTransformerImpl

    private val emptyPayloadResult = TealiumResult.success(DataObject.EMPTY_OBJECT.asDataItem())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { mockDispatch.payload() } returns DataObject.EMPTY_OBJECT
        transformer = JavaScriptTransformerImpl(mockAdapter, mockLogger, Scheduler.SYNCHRONOUS)
    }

    private fun settingsWithCode(jsCode: String): TransformationSettings =
        buildTransformationsSettings(jsCode)

    private fun settingsWithNoCode(): TransformationSettings =
        buildTransformationsSettings(null)

    private fun buildTransformationsSettings(jsCode: String?): TransformationSettings {
        val builder = JavaScriptTransformationSettingsBuilder(
            transformationId = "test_transformation"
        ).setScope(TransformationScope.AfterCollectors) // scope is required
        jsCode?.let { code -> builder.setJsCode(code) }

        return builder.buildTransformationSettings()
    }

    private fun stubAdapterResult(result: TealiumResult<DataItem>) {
        every { mockAdapter.evaluateJavaScript(any(), any()) } answers {
            secondArg<Callback<TealiumResult<DataItem>>>().onComplete(result)
            Disposables.disposed()
        }
    }

    private fun stubAdapterCapturingJs(
        result: TealiumResult<DataItem> = emptyPayloadResult
    ): CapturingSlot<String> {
        val jsSlot = slot<String>()
        every { mockAdapter.evaluateJavaScript(capture(jsSlot), any()) } answers {
            secondArg<Callback<TealiumResult<DataItem>>>().onComplete(result)
            Disposables.disposed()
        }
        return jsSlot
    }

    private fun stubAdapterCapturingCallback(
        evaluation: Disposable = Disposables.subscription()
    ): CapturingSlot<Callback<TealiumResult<DataItem>>> {
        val callbackSlot = slot<Callback<TealiumResult<DataItem>>>()
        every { mockAdapter.evaluateJavaScript(any(), capture(callbackSlot)) } returns evaluation
        return callbackSlot
    }

    private fun verifyAdapterNotInvoked() {
        verify(inverse = true) { mockAdapter.evaluateJavaScript(any(), any()) }
    }

    @Test
    fun applyTransformation_CallsCompletionWithDispatch_WhenJsCodeIsNull() {
        val completion = mockk<Callback<Dispatch?>>(relaxed = true)

        transformer.applyTransformation(
            settingsWithNoCode(),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify { completion.onComplete(mockDispatch) }
        verifyAdapterNotInvoked()
    }

    @Test
    fun applyTransformation_CallsCompletionWithDispatch_WhenJsCodeIsBlank() {
        val completion = mockk<Callback<Dispatch?>>(relaxed = true)

        transformer.applyTransformation(
            settingsWithCode("   "),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify { completion.onComplete(mockDispatch) }
        verifyAdapterNotInvoked()
    }

    @Test
    fun applyTransformation_CallsCompletionWithDispatch_WhenJsCodeIsEmpty() {
        val completion = mockk<Callback<Dispatch?>>(relaxed = true)

        transformer.applyTransformation(
            settingsWithCode(""),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify { completion.onComplete(mockDispatch) }
        verifyAdapterNotInvoked()
    }

    @Test
    fun applyTransformation_ReplacesDispatchPayload_WhenAdapterReturnsDataObject() {
        val originalPayload = DataObject.create { put("original_key", "original_value") }
        every { mockDispatch.payload() } returns originalPayload

        val newPayload = DataObject.create { put("modified_key", "modified_value") }
        stubAdapterResult(TealiumResult.success(newPayload.asDataItem()))

        val completion = mockk<Callback<Dispatch?>>(relaxed = true)
        transformer.applyTransformation(
            settingsWithCode("payload.modified_key = 'modified_value';"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify { mockDispatch.replace(newPayload) }
        verify { completion.onComplete(mockDispatch) }
    }

    @Test
    fun applyTransformation_CallsCompletionWithNull_WhenAdapterReturnsNullDataItem() {
        stubAdapterResult(TealiumResult.success(DataItem.NULL))

        val completion = mockk<Callback<Dispatch?>>(relaxed = true)
        transformer.applyTransformation(
            settingsWithCode("drop()"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify { completion.onComplete(null) }
        verify(inverse = true) { mockDispatch.replace(any()) }
    }

    @Test
    fun applyTransformation_CallsCompletionWithDispatch_WhenAdapterResultThrows() {
        stubAdapterResult(TealiumResult.failure(RuntimeException("JS evaluation error")))

        val completion = mockk<Callback<Dispatch?>>(relaxed = true)
        transformer.applyTransformation(
            settingsWithCode("throw new Error('boom')"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify { completion.onComplete(mockDispatch) }
    }

    @Test
    fun applyTransformation_DoesNotReplacePayload_WhenAdapterResultThrows() {
        stubAdapterResult(TealiumResult.failure(RuntimeException("JS evaluation error")))

        val completion = mockk<Callback<Dispatch?>>(relaxed = true)
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

        val jsSlot = stubAdapterCapturingJs()

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

        val jsSlot = stubAdapterCapturingJs()

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
        val jsSlot = stubAdapterCapturingJs()

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
        val jsSlot = stubAdapterCapturingJs()

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
        val jsSlot = stubAdapterCapturingJs()

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
        val jsSlot = stubAdapterCapturingJs()

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
    fun applyTransformation_DoesNotRetainEvaluation_WhenAdapterCompletesAsynchronously() {
        val evaluation = Disposables.subscription()
        val callbackSlot = stubAdapterCapturingCallback(evaluation)

        transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            mockk(relaxed = true)
        )
        callbackSlot.captured.onComplete(emptyPayloadResult)
        transformer.onShutdown()

        assertFalse(evaluation.isDisposed)
    }

    @Test
    fun applyTransformation_DoesNotRetainEvaluation_WhenAdapterCompletesBeforeReturning() {
        val evaluation = Disposables.subscription()
        every { mockAdapter.evaluateJavaScript(any(), any()) } answers {
            secondArg<Callback<TealiumResult<DataItem>>>()
                .onComplete(emptyPayloadResult)
            evaluation
        }

        transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            mockk(relaxed = true)
        )
        transformer.onShutdown()

        assertFalse(evaluation.isDisposed)
    }

    @Test
    fun applyTransformation_IgnoresRepeatedCompletion_WhenAdapterCompletesTwice() {
        val callbackSlot = stubAdapterCapturingCallback()

        val completion = mockk<Callback<Dispatch?>>(relaxed = true)
        transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )
        val result = emptyPayloadResult
        callbackSlot.captured.onComplete(result)
        callbackSlot.captured.onComplete(result)

        verify(exactly = 1) { completion.onComplete(any()) }
        verify(exactly = 1) { mockDispatch.replace(any()) }
    }

    @Test
    fun applyTransformation_DoesNotComplete_WhenAdapterCompletesAfterShutdown() {
        val callbackSlot = stubAdapterCapturingCallback()

        val completion = mockk<Callback<Dispatch?>>(relaxed = true)
        transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )
        transformer.onShutdown()
        callbackSlot.captured.onComplete(emptyPayloadResult)

        verify(exactly = 0) { completion.onComplete(any()) }
        verify(exactly = 0) { mockDispatch.replace(any()) }
    }

    @Test
    fun applyTransformation_DoesNotEvaluate_WhenAlreadyShutdown() {
        transformer.onShutdown()

        val completion = mockk<Callback<Dispatch?>>(relaxed = true)
        transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verifyAdapterNotInvoked()
        verify(exactly = 0) { completion.onComplete(any()) }
    }

    @Test
    fun applyTransformation_DefersCompletionToScheduler_WhenAdapterCompletesOffThread() {
        val scheduler = ManualScheduler()
        val callbackSlot = stubAdapterCapturingCallback()
        val completion = mockk<Callback<Dispatch?>>(relaxed = true)

        JavaScriptTransformerImpl(mockAdapter, mockLogger, scheduler).applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )
        callbackSlot.captured.onComplete(emptyPayloadResult)

        verify(exactly = 0) { completion.onComplete(any()) }

        scheduler.runAll()

        verify(exactly = 1) { completion.onComplete(any()) }
    }

    @Test
    fun applyTransformation_DoesNotComplete_WhenShutdownBeforeQueuedCompletionRuns() {
        val scheduler = ManualScheduler()
        val callbackSlot = stubAdapterCapturingCallback()
        val completion = mockk<Callback<Dispatch?>>(relaxed = true)
        val offThreadTransformer = JavaScriptTransformerImpl(mockAdapter, mockLogger, scheduler)

        offThreadTransformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )
        callbackSlot.captured.onComplete(emptyPayloadResult)
        offThreadTransformer.onShutdown()
        scheduler.runAll()

        verify(exactly = 0) { completion.onComplete(any()) }
        verify(exactly = 0) { mockDispatch.replace(any()) }
    }

    @Test
    fun applyTransformation_IgnoresRepeatedCompletion_WhenQueuedCompletionsRun() {
        val scheduler = ManualScheduler()
        val callbackSlot = stubAdapterCapturingCallback()
        val completion = mockk<Callback<Dispatch?>>(relaxed = true)

        JavaScriptTransformerImpl(mockAdapter, mockLogger, scheduler).applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )
        val result = emptyPayloadResult
        callbackSlot.captured.onComplete(result)
        callbackSlot.captured.onComplete(result)
        scheduler.runAll()

        verify(exactly = 1) { completion.onComplete(any()) }
        verify(exactly = 1) { mockDispatch.replace(any()) }
    }

    @Test
    fun onShutdown_DisposesEvaluation_WhenAdapterHasNotCompleted() {
        val evaluation = Disposables.subscription()
        every { mockAdapter.evaluateJavaScript(any(), any()) } returns evaluation

        transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            mockk(relaxed = true)
        )
        transformer.onShutdown()

        assertTrue(evaluation.isDisposed)
    }

    @Test
    fun applyTransformation_Returns_Disposed_Disposable_When_Evaluation_Completes() {
        stubAdapterResult(emptyPayloadResult)

        val completion = mockk<Callback<Dispatch?>>(relaxed = true)
        val disposable = transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify(exactly = 1) { completion.onComplete(mockDispatch) }
        verify(exactly = 1) { completion.onComplete(any()) }
        assertTrue(disposable.isDisposed)
    }

    @Test
    fun applyTransformation_Returns_Active_Disposable_When_Evaluation_Is_Pending() {
        val callbackSlot = stubAdapterCapturingCallback()

        val completion = mockk<Callback<Dispatch?>>(relaxed = true)
        val disposable = transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        assertFalse(disposable.isDisposed)
        verify(exactly = 0) { completion.onComplete(any()) }

        callbackSlot.captured.onComplete(emptyPayloadResult)

        verify(exactly = 1) { completion.onComplete(mockDispatch) }
        verify(exactly = 1) { completion.onComplete(any()) }
        assertTrue(disposable.isDisposed)
    }

    @Test
    fun applyTransformation_Does_Not_Complete_When_Disposed_Before_Evaluation_Completes() {
        val callbackSlot = stubAdapterCapturingCallback()

        val completion = mockk<Callback<Dispatch?>>(relaxed = true)
        val disposable = transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        disposable.dispose()
        callbackSlot.captured.onComplete(emptyPayloadResult)

        verify(exactly = 0) { completion.onComplete(any()) }
        verify(exactly = 0) { mockDispatch.replace(any()) }
    }

    @Test
    fun applyTransformation_Cancels_Evaluation_When_Returned_Disposable_Is_Disposed_While_Pending() {
        val evaluation = Disposables.subscription()
        stubAdapterCapturingCallback(evaluation)

        val disposable = transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            mockk(relaxed = true)
        )
        disposable.dispose()

        assertTrue(evaluation.isDisposed)
    }

    @Test
    fun applyTransformation_Does_Not_Cancel_Evaluation_When_Returned_Disposable_Is_Disposed_After_Completion() {
        val evaluation = Disposables.subscription()
        val callbackSlot = stubAdapterCapturingCallback(evaluation)

        val disposable = transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            mockk(relaxed = true)
        )
        callbackSlot.captured.onComplete(emptyPayloadResult)
        disposable.dispose()

        assertFalse(evaluation.isDisposed)
    }

    @Test
    fun applyTransformation_Returns_Disposed_Disposable_When_Js_Code_Is_Null() {
        val completion = mockk<Callback<Dispatch?>>(relaxed = true)
        val disposable = transformer.applyTransformation(
            settingsWithNoCode(),
            mockDispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify(exactly = 1) { completion.onComplete(mockDispatch) }
        verify(exactly = 1) { completion.onComplete(any()) }
        assertTrue(disposable.isDisposed)
    }

    @Test
    fun applyTransformation_Returns_Disposed_Disposable_When_Already_Shutdown() {
        transformer.onShutdown()

        val disposable = transformer.applyTransformation(
            settingsWithCode("// noop"),
            mockDispatch,
            DispatchScope.AfterCollectors,
            mockk(relaxed = true)
        )

        assertTrue(disposable.isDisposed)
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
