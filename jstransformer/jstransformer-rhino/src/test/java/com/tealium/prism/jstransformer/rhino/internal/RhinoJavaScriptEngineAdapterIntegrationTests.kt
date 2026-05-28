package com.tealium.prism.jstransformer.rhino.internal

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.network.HttpMethod
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.HttpResponse
import com.tealium.prism.core.api.network.NetworkClient
import com.tealium.prism.core.api.network.NetworkException
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.Tracker
import com.tealium.prism.jstransformer.JavaScriptTransformerFactory
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class RhinoJavaScriptEngineAdapterIntegrationTests {

    @RelaxedMockK
    private lateinit var mockLogger: Logger

    @RelaxedMockK
    private lateinit var mockDataLayer: DataStore

    @RelaxedMockK
    private lateinit var mockDataLayerEditor: DataStore.Editor

    @RelaxedMockK
    private lateinit var mockTracker: Tracker

    private lateinit var adapter: RhinoJavaScriptEngineAdapter

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { mockDataLayer.edit() } returns mockDataLayerEditor
        every { mockDataLayerEditor.put(any(), any<DataItem>(), any()) } returns mockDataLayerEditor
        every { mockDataLayerEditor.clear() } returns mockDataLayerEditor

        val rhinoObjects = RhinoJavaScriptEngineAdapter.setupRhinoObjects(mockTracker, mockLogger, mockDataLayer)
        adapter = RhinoJavaScriptEngineAdapter(rhinoObjects)

    }

    @After
    fun tearDown() {
        if (!adapter.isDisposed) {
            adapter.dispose()
        }
    }

    private fun eval(js: String): Observable<TealiumResult<DataItem>> =
        adapter.evaluateJavaScript(js)

    private fun evalAndGet(js: String): DataItem? {
        var result: DataItem? = null
        eval(js).subscribe { tResult -> result = tResult.getOrNull() }
        return result
    }

    @Test
    fun console_log_DelegatesToLoggerWithDebugLevel() {
        eval("console.log('hello')")

        verify { mockLogger.log(LogLevel.DEBUG, JavaScriptTransformerFactory.MODULE_TYPE, "hello") }
    }

    @Test
    fun console_info_DelegatesToLoggerWithInfoLevel() {
        eval("console.info('info message')")

        verify { mockLogger.log(LogLevel.INFO, JavaScriptTransformerFactory.MODULE_TYPE, "info message") }
    }

    @Test
    fun console_warn_DelegatesToLoggerWithWarnLevel() {
        eval("console.warn('warning')")

        verify { mockLogger.log(LogLevel.WARN, JavaScriptTransformerFactory.MODULE_TYPE, "warning") }
    }

    @Test
    fun console_error_DelegatesToLoggerWithErrorLevel() {
        eval("console.error('error message')")

        verify { mockLogger.log(LogLevel.ERROR, JavaScriptTransformerFactory.MODULE_TYPE, "error message") }
    }

    @Test
    fun console_log_JoinsMultipleArguments_WithSpace() {
        eval("console.log('hello', 'world')")

        verify { mockLogger.log(LogLevel.DEBUG, JavaScriptTransformerFactory.MODULE_TYPE, "hello world") }
    }

    @Test
    fun dataLayer_get_RetrievesValueFromDataStore() {
        every { mockDataLayer.get("my_key") } returns DataItem.string("my_value")

        eval("dataLayer.get('my_key')")

        verify { mockDataLayer.get("my_key") }
    }

    @Test
    fun dataLayer_getAll_RetrievesAllValuesFromDataStore() {
        every { mockDataLayer.getAll() } returns DataObject.EMPTY_OBJECT

        eval("dataLayer.getAll()")

        verify { mockDataLayer.getAll() }
    }

    @Test
    fun dataLayer_put_StoresValueWithForeverExpiry() {
        eval("dataLayer.put('my_key', 'my_value')")

        verify { mockDataLayerEditor.put("my_key", DataItem.string("my_value"), Expiry.FOREVER) }
        verify { mockDataLayerEditor.commit() }
    }

    @Test
    fun dataLayer_put_WithExpiryConstant_StoresValueWithCorrectExpiry() {
        eval("dataLayer.put('my_key', 'my_value', Expiry.session)")

        verify { mockDataLayerEditor.put("my_key", DataItem.string("my_value"), Expiry.SESSION) }
        verify { mockDataLayerEditor.commit() }
    }

    @Test
    fun dataLayer_clear_ClearsAllEntriesInDataStore() {
        eval("dataLayer.clear()")

        verify { mockDataLayerEditor.clear() }
        verify { mockDataLayerEditor.commit() }
    }

    @Test
    fun tracker_track_WithEventName_DispatchesEventTypeByDefault() {
        val dispatchSlot = slot<Dispatch>()
        every { mockTracker.track(capture(dispatchSlot), any()) } just Runs

        eval("tracker.track('my_event')")

        assertEquals("my_event", dispatchSlot.captured.tealiumEvent)
        assertEquals(DispatchType.Event, dispatchSlot.captured.type)
    }

    @Test
    fun tracker_track_WithViewEventType_DispatchesViewType() {
        val dispatchSlot = slot<Dispatch>()
        every { mockTracker.track(capture(dispatchSlot), any()) } just Runs

        eval("tracker.track('my_event', 'view')")

        assertEquals(DispatchType.View, dispatchSlot.captured.type)
    }

    @Test
    fun tracker_track_SetsJsTrackingFlag_ToPreventRecursion() {
        val dispatchSlot = slot<Dispatch>()
        every { mockTracker.track(capture(dispatchSlot), any()) } just Runs

        eval("tracker.track('my_event')")

        assertEquals(true, dispatchSlot.captured.payload().getBoolean("js_tracking"))
    }

    @Test
    fun tracker_track_WithPayload_ForwardsCustomProperties() {
        val dispatchSlot = slot<Dispatch>()
        every { mockTracker.track(capture(dispatchSlot), any()) } just Runs

        eval("tracker.track('my_event', {custom_key: 'custom_value'})")

        assertEquals("custom_value", dispatchSlot.captured.payload().getString("custom_key"))
    }

    @Test
    fun tracker_track_WithEventTypeAndPayload_DispatchesCorrectly() {
        val dispatchSlot = slot<Dispatch>()
        every { mockTracker.track(capture(dispatchSlot), any()) } just Runs

        eval("tracker.track('my_event', 'view', {custom_key: 'custom_value'})")

        assertEquals(DispatchType.View, dispatchSlot.captured.type)
        assertEquals("custom_value", dispatchSlot.captured.payload().getString("custom_key"))
    }

//    @Test
//    fun network_get_SendsGetRequestToHttpClient() {
//        val requestSlot = slot<HttpRequest>()
//        every { mockNetworkClient.sendRequest(capture(requestSlot), any()) } returns Disposables.disposed()
//
//        eval("network.get('http://example.com', function(res) {})")
//
//        assertEquals(HttpMethod.Get, requestSlot.captured.method)
//        assertEquals("http://example.com", requestSlot.captured.url.toString())
//    }
//
//    @Test
//    fun network_post_SendsPostRequestToHttpClient() {
//        val requestSlot = slot<HttpRequest>()
//        every { mockNetworkClient.sendRequest(capture(requestSlot), any()) } returns Disposables.disposed()
//
//        eval("network.post('http://example.com', 'request body', function(res) {})")
//
//        assertEquals(HttpMethod.Post, requestSlot.captured.method)
//        assertEquals("request body", requestSlot.captured.body)
//    }
//
//    @Test
//    fun network_get_InvokesJsCallback_OnSuccess() {
//        val httpResponse = HttpResponse(
//            url = URL("http://example.com"),
//            statusCode = 200,
//            message = "OK",
//            headers = emptyMap()
//        )
//        every { mockNetworkClient.sendRequest(any(), any()) } answers {
//            secondArg<Callback<NetworkResult>>().onComplete(NetworkResult.Success(httpResponse))
//            Disposables.disposed()
//        }
//
//        val result = evalAndGet("""
//            var statusCode = -1;
//            network.get('http://example.com', function(res) {
//                statusCode = res.statusCode;
//            });
//            statusCode;
//        """.trimIndent())
//
//        assertEquals(200, result!!.getInt())
//    }
//
//    @Test
//    fun network_get_InvokesJsCallback_OnFailure() {
//        val networkException = NetworkException.Non200Exception(404)
//        every { mockNetworkClient.sendRequest(any(), any()) } answers {
//            secondArg<Callback<NetworkResult>>().onComplete(NetworkResult.Failure(networkException))
//            Disposables.disposed()
//        }
//
//        val result = evalAndGet("""
//            var callbackInvoked = false;
//            network.get('http://example.com', function(err) {
//                callbackInvoked = true;
//            });
//            callbackInvoked;
//        """.trimIndent())
//
//        assertTrue(result!!.getBoolean() == true)
//    }

    @Test
    fun expiry_Session_IsAccessibleFromJs_WithExpectedValue() {
        val result = evalAndGet("Expiry.session")

        assertEquals(Expiry.SESSION.expiryTime(), result!!.getLong())
    }

    @Test
    fun expiry_Forever_IsAccessibleFromJs_WithExpectedValue() {
        val result = evalAndGet("Expiry.forever")

        assertEquals(Expiry.FOREVER.expiryTime(), result!!.getLong())
    }

    @Test
    fun expiry_UntilRestart_IsAccessibleFromJs_WithExpectedValue() {
        val result = evalAndGet("Expiry.untilRestart")

        assertEquals(Expiry.UNTIL_RESTART.expiryTime(), result!!.getLong())
    }
}
