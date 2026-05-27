package com.tealium.prism.jstransformer.rhino.internal

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Observable
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RhinoJavaScriptEngineAdapterTests {

    private lateinit var rhinoContext: Context
    private lateinit var scope: ScriptableObject
    private lateinit var adapter: RhinoJavaScriptEngineAdapter

    @Before
    fun setUp() {
        rhinoContext = Context.enter()
        rhinoContext.isInterpretedMode = true
        scope = rhinoContext.initSafeStandardObjects()
        adapter = RhinoJavaScriptEngineAdapter(RhinoObjects(rhinoContext, scope))
    }

    @After
    fun tearDown() {
        if (!adapter.isDisposed) {
            adapter.dispose()
        }
    }

    private fun collectResult(observable: Observable<TealiumResult<DataItem>>): TealiumResult<DataItem>? {
        var result: TealiumResult<DataItem>? = null
        observable.subscribe { result = it }
        return result
    }

    private fun collectSuccess(observable: Observable<TealiumResult<DataItem>>): DataItem {
        return collectResult(observable)!!.getOrThrow()
    }

    @Test
    fun evaluateJavaScript_ReturnsSuccess_WithDataObject_WhenJsReturnsJsonObjectString() {
        val result = collectSuccess(adapter.evaluateJavaScript("""'{"key":"value"}'"""))

        assertEquals("value", result.getDataObject()!!.getString("key"))
    }

    @Test
    fun evaluateJavaScript_ReturnsSuccess_WithNumber_WhenJsReturnsNumber() {
        val result = collectSuccess(adapter.evaluateJavaScript("42"))

        assertEquals(42, result.getInt())
    }

    @Test
    fun evaluateJavaScript_ReturnsSuccess_WithDouble_WhenJsReturnsDecimalNumber() {
        val result = collectSuccess(adapter.evaluateJavaScript("3.14"))

        assertEquals(3.14, result.getDouble()!!, 0.001)
    }

    @Test
    fun evaluateJavaScript_ReturnsSuccess_WithBooleanTrue_WhenJsReturnsTrue() {
        val result = collectSuccess(adapter.evaluateJavaScript("true"))

        assertEquals(true, result.getBoolean())
    }

    @Test
    fun evaluateJavaScript_ReturnsSuccess_WithNullDataItem_WhenJsReturnsNull() {
        val result = collectSuccess(adapter.evaluateJavaScript("null"))

        assertTrue(result.isNull())
    }

    @Test
    fun evaluateJavaScript_ReturnsFailure_WhenJsThrowsException() {
        val result = collectResult(adapter.evaluateJavaScript("throw new Error('boom')"))

        assertTrue(result!!.isFailure)
    }

    @Test
    fun isDisposed_ReturnsFalse_WhenAdapterIsNew() {
        assertFalse(adapter.isDisposed)
    }

    @Test
    fun isDisposed_ReturnsTrue_AfterDispose() {
        adapter.dispose()

        assertTrue(adapter.isDisposed)
    }

    @Test
    fun evaluateJavaScript_ReturnsFailure_WhenAdapterIsDisposed() {
        adapter.dispose()

        val result = collectResult(adapter.evaluateJavaScript("'test'"))

        assertTrue(result!!.isFailure)
    }

    @Test
    fun evaluateJavaScript_ReturnsExpectedErrorMessage_WhenAdapterIsDisposed() {
        adapter.dispose()

        val result = collectResult(adapter.evaluateJavaScript("'test'"))

        assertEquals("Js Engine already shutdown", result!!.exceptionOrNull()!!.message)
    }

    @Test
    fun dispose_ClosesRhinoContext() {
        val mockContext = mockk<Context>(relaxed = true)
        val mockScope = mockk<ScriptableObject>(relaxed = true)
        val mockAdapter = RhinoJavaScriptEngineAdapter(RhinoObjects(mockContext, mockScope))

        mockAdapter.dispose()

        verify { mockContext.close() }
    }
}
